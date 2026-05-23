package com.sokolov.labs.worker.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sokolov.labs.shared.dto.InferenceTaskMessage;
import com.sokolov.labs.shared.dto.TaskResultsPayload;
import com.sokolov.labs.shared.dto.TaskStatus;
import com.sokolov.labs.worker.messaging.StatusPublisher;
import com.sokolov.labs.worker.storage.StorageClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class InferenceRunner {

    private static final Logger log = LoggerFactory.getLogger(InferenceRunner.class);
    private static final String MANIFEST_NAME = "manifest.json";
    private static final String LABELS_PREFIX = "labels/";
    private static final String IMAGES_PREFIX = "images/";
    private static final String VAL_SEGMENT = "/val/";
    private static final String TRAIN_SEGMENT = "/train/";

    private final StorageClient storage;
    private final StatusPublisher publisher;
    private final MeterRegistry meterRegistry;
    private final CancellationRegistry cancellationRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InferenceRunner(StorageClient storage, StatusPublisher publisher,
                           MeterRegistry meterRegistry,
                           CancellationRegistry cancellationRegistry) {
        this.storage = storage;
        this.publisher = publisher;
        this.meterRegistry = meterRegistry;
        this.cancellationRegistry = cancellationRegistry;
    }

    public void run(InferenceTaskMessage task) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "succeeded";
        try {
            doRun(task);
        } catch (Exception e) {
            outcome = "failed";
            throw e;
        } finally {
            sample.stop(Timer.builder("inference_duration_seconds")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    private void doRun(InferenceTaskMessage task) throws Exception {
        if (cancellationRegistry.isCancelled(task.taskId())) {
            log.info("Task {} is cancelled before processing started — skipping", task.taskId());
            publisher.publish(task.taskId(), task.ownerId(), TaskStatus.CANCELED, 0,
                    "Cancelled by user", null, null);
            return;
        }
        publisher.publish(task.taskId(), task.ownerId(), TaskStatus.RUNNING, 5, "Started", null, null);

        byte[] modelBytes = storage.download(task.modelS3Key());
        byte[] zipBytes = storage.download(task.datasetS3Key());
        publisher.publish(task.taskId(), task.ownerId(), TaskStatus.RUNNING, 15,
                "Artifacts downloaded", null, null);

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        // Allow unreleased ONNX opsets (ultralytics exports with opset 22 by default).
        opts.addConfigEntry("session.allow_released_opsets_only", "0");
        try (OrtSession session = env.createSession(modelBytes, opts)) {
            String inputName = session.getInputNames().iterator().next();
            String outputName = session.getOutputNames().iterator().next();
            long[] inputShape = ((TensorInfo) session.getInputInfo().get(inputName).getInfo()).getShape();
            long[] effectiveShape = effectiveShape(inputShape);
            boolean imageInput = isImageInput(effectiveShape);

            Map<String, byte[]> entries = readZip(zipBytes);
            Map<String, Integer> manifest = readManifest(entries.remove(MANIFEST_NAME));
            Map<String, byte[]> labels = takeLabels(entries);
            List<String> samples = selectSamples(entries);
            if (samples.isEmpty()) {
                throw new IllegalArgumentException("Dataset archive contains no samples");
            }
            log.info("Dataset: {} samples selected, {} labels available", samples.size(), labels.size());

            StringBuilder csv = new StringBuilder();
            List<TaskResultsPayload.ImageResult> imageResults = new ArrayList<>();
            int total = samples.size();
            int correctCls = 0;
            int labeledCls = 0;
            double pckSum = 0;
            int pckSamples = 0;
            double recallSum = 0;
            int recallSamples = 0;
            String taskPrefix = "results/" + task.ownerId() + "/" + task.taskId();
            String imagesPrefix = taskPrefix + "/images/";

            if (!imageInput) {
                csv.append("filename,predicted,true_label\n");
            }

            for (int i = 0; i < total; i++) {
                if (cancellationRegistry.isCancelled(task.taskId())) {
                    log.info("Task {} cancelled by user after {} of {} samples", task.taskId(), i, total);
                    publisher.publish(task.taskId(), task.ownerId(), TaskStatus.CANCELED,
                            (int) Math.round(i * 100.0 / Math.max(1, total)),
                            "Cancelled by user after " + i + " of " + total + " samples",
                            null, null);
                    return;
                }
                String name = samples.get(i);
                byte[] data = entries.get(name);

                if (imageInput) {
                    int height = (int) effectiveShape[2];
                    int width = (int) effectiveShape[3];
                    ImageLoader.Loaded lb = ImageLoader.loadLetterboxRgb(data, height, width);
                    long[] shape = {1, 3, height, width};

                    String imageKey = imagesPrefix + stripImagesPrefix(name);
                    storage.upload(imageKey, data, contentTypeFor(name));

                    try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(lb.tensor()), shape);
                         OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
                        OnnxValue out = result.get(outputName).orElseThrow();
                        List<Detection> dets = YoloOutputParser.parse(out.getValue(), width, height, lb,
                                YoloOutputParser.DEFAULT_CONF, YoloOutputParser.DEFAULT_IOU);

                        List<Detection> truthDets = List.of();
                        byte[] labelBytes = lookupLabel(labels, name);
                        if (labelBytes != null && task.labeled()) {
                            truthDets = parseLabel(labelBytes, lb.origWidth(), lb.origHeight());
                            AccuracyCalculator.FrameScore score = AccuracyCalculator.evaluate(dets, truthDets);
                            if (!Double.isNaN(score.detectionRecall())) {
                                recallSum += score.detectionRecall();
                                recallSamples++;
                            }
                            if (!Double.isNaN(score.pck()) && score.keypointPairs() > 0) {
                                pckSum += score.pck();
                                pckSamples++;
                            }
                        }

                        imageResults.add(new TaskResultsPayload.ImageResult(
                                name, imageKey, lb.origWidth(), lb.origHeight(),
                                toDetectionDtos(dets), toDetectionDtos(truthDets)));
                    }
                } else {
                    int expected = expectedSize(effectiveShape);
                    float[] input = bytesToFloats(data, expected);
                    int predicted;
                    try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), effectiveShape);
                         OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
                        predicted = argmax(result.get(outputName).orElseThrow());
                    }
                    Integer label = manifest.get(name);
                    if (label != null) {
                        labeledCls++;
                        if (label == predicted) correctCls++;
                    }
                    csv.append(name).append(',').append(predicted).append(',')
                            .append(label == null ? "" : label).append('\n');
                }

                int pct = 15 + (int) Math.round((i + 1) * 80.0 / total);
                if (i == total - 1 || pct % 10 == 0) {
                    publisher.publish(task.taskId(), task.ownerId(), TaskStatus.RUNNING, pct,
                            "Processed " + (i + 1) + " / " + total, null, null);
                }
            }

            Double accuracy = null;
            String summary;
            String resultKey;
            if (imageInput) {
                Double recall = recallSamples > 0 ? recallSum / recallSamples : null;
                Double pck = pckSamples > 0 ? pckSum / pckSamples : null;
                if (pck != null) accuracy = pck;
                else if (recall != null) accuracy = recall;

                TaskResultsPayload payload = new TaskResultsPayload(
                        java.util.Arrays.toString(effectiveShape),
                        task.labeled(),
                        recall, pck,
                        imageResults);
                resultKey = taskPrefix + "/results.json";
                storage.upload(resultKey,
                        objectMapper.writeValueAsBytes(payload),
                        "application/json");

                summary = String.format("Processed %d images; recall=%s pck@%dpx=%s",
                        total,
                        recall == null ? "n/a" : String.format("%.4f", recall),
                        (int) AccuracyCalculator.KEYPOINT_PIXEL_THRESHOLD,
                        pck == null ? "n/a" : String.format("%.4f", pck));
            } else {
                if (task.labeled() && labeledCls > 0) accuracy = (double) correctCls / labeledCls;
                resultKey = taskPrefix + "/results.csv";
                storage.upload(resultKey, csv.toString().getBytes(StandardCharsets.UTF_8), "text/csv");
                summary = "Processed " + total + " samples"
                        + (accuracy != null ? String.format(", accuracy=%.4f", accuracy) : "");
            }
            publisher.publish(task.taskId(), task.ownerId(), TaskStatus.SUCCEEDED, 100,
                    summary, resultKey, accuracy);
            log.info("Task {} succeeded ({})", task.taskId(), summary);
        }
    }

    private static String stripImagesPrefix(String name) {
        return name.startsWith("images/") ? name.substring("images/".length()) : name;
    }

    private static String contentTypeFor(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static List<TaskResultsPayload.Detection> toDetectionDtos(List<Detection> dets) {
        List<TaskResultsPayload.Detection> out = new ArrayList<>(dets.size());
        for (Detection d : dets) {
            out.add(new TaskResultsPayload.Detection(
                    d.x1(), d.y1(), d.x2(), d.y2(),
                    d.confidence(), d.classId(),
                    d.keypoints()));
        }
        return out;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    private static boolean isImageInput(long[] shape) {
        return shape.length == 4 && shape[1] == 3 && shape[2] > 8 && shape[3] > 8;
    }

    private static long[] effectiveShape(long[] modelShape) {
        long[] shape = new long[modelShape.length];
        for (int i = 0; i < modelShape.length; i++) {
            shape[i] = modelShape[i] <= 0 ? 1 : modelShape[i];
        }
        return shape;
    }

    private static int expectedSize(long[] shape) {
        long size = 1;
        for (long d : shape) {
            size *= d;
        }
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Input tensor too large: " + size);
        }
        return (int) size;
    }

    private static float[] bytesToFloats(byte[] data, int expectedFloats) {
        if (data.length != expectedFloats * Float.BYTES) {
            throw new IllegalArgumentException("Sample size " + data.length
                    + " bytes does not match expected " + (expectedFloats * Float.BYTES));
        }
        FloatBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] out = new float[expectedFloats];
        buf.get(out);
        return out;
    }

    private static int argmax(OnnxValue value) {
        Object raw = unwrap(value);
        if (raw instanceof float[] flat) return argmaxArray(flat);
        if (raw instanceof float[][] m && m.length > 0) return argmaxArray(m[0]);
        throw new IllegalStateException("Unsupported output type: " + raw.getClass());
    }

    private static Object unwrap(OnnxValue value) {
        try {
            return value.getValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read output", e);
        }
    }

    private static int argmaxArray(float[] arr) {
        int idx = 0;
        float best = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > best) {
                best = arr[i];
                idx = i;
            }
        }
        return idx;
    }

    private static Map<String, byte[]> readZip(byte[] bytes) throws java.io.IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                out.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return out;
    }

    private Map<String, Integer> readManifest(byte[] manifest) throws java.io.IOException {
        if (manifest == null) {
            return new HashMap<>();
        }
        return objectMapper.readValue(manifest, new TypeReference<>() {});
    }

    /**
     * Pulls {@code labels/...} entries out of the dataset map, keyed by the path relative
     * to {@code labels/} with the {@code .txt} extension stripped. Supports both flat
     * {@code labels/foo.txt} and {@code labels/<split>/foo.txt} (YOLO standard layout).
     */
    private static Map<String, byte[]> takeLabels(Map<String, byte[]> entries) {
        Map<String, byte[]> labels = new HashMap<>();
        var iter = entries.entrySet().iterator();
        while (iter.hasNext()) {
            var e = iter.next();
            String key = e.getKey();
            if (key.startsWith(LABELS_PREFIX) && key.endsWith(".txt")) {
                String rel = key.substring(LABELS_PREFIX.length(), key.length() - ".txt".length());
                labels.put(rel, e.getValue());
                iter.remove();
            }
        }
        return labels;
    }

    /**
     * Picks samples from the dataset, supporting two layouts:
     * <ol>
     *   <li>flat — every remaining entry is a sample</li>
     *   <li>YOLO with {@code images/...}: prefers {@code images/val/*}, falls back to
     *       {@code images/*} if no val split exists; {@code images/train/*} is ignored.</li>
     * </ol>
     */
    private static List<String> selectSamples(Map<String, byte[]> entries) {
        boolean hasImagesPrefix = entries.keySet().stream().anyMatch(k -> k.startsWith(IMAGES_PREFIX));
        List<String> result = new ArrayList<>();
        if (hasImagesPrefix) {
            boolean hasVal = entries.keySet().stream().anyMatch(k -> k.contains(VAL_SEGMENT));
            for (String key : entries.keySet()) {
                if (!key.startsWith(IMAGES_PREFIX)) continue;
                if (hasVal && !key.contains(VAL_SEGMENT)) continue;
                if (!hasVal && key.contains(TRAIN_SEGMENT)) continue;
                result.add(key);
            }
        } else {
            result.addAll(entries.keySet());
        }
        Collections.sort(result);
        return result;
    }

    /**
     * For sample {@code images/val/foo.jpg} returns label bytes from {@code labels/val/foo.txt}.
     * For flat {@code foo.jpg}: tries {@code labels/foo.txt}.
     * Returns null if not found.
     */
    private static byte[] lookupLabel(Map<String, byte[]> labels, String sampleName) {
        String relative = sampleName.startsWith(IMAGES_PREFIX)
                ? sampleName.substring(IMAGES_PREFIX.length())
                : sampleName;
        return labels.get(stripExtension(relative));
    }

    private List<Detection> parseLabel(byte[] bytes, int imageWidth, int imageHeight) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return YoloLabel.parse(text).stream()
                .map(a -> YoloLabel.toDetection(a, imageWidth, imageHeight))
                .toList();
    }

    @SuppressWarnings("unused")
    private static String legacyCsvDetections(List<Detection> dets) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dets.size(); i++) {
            Detection d = dets.get(i);
            if (i > 0) sb.append(';');
            sb.append(String.format("cls=%d conf=%.3f bbox=%.1f,%.1f,%.1f,%.1f",
                    d.classId(), d.confidence(), d.x1(), d.y1(), d.x2(), d.y2()));
            if (!d.keypoints().isEmpty()) {
                sb.append(" kpts=");
                for (int k = 0; k < d.keypoints().size(); k++) {
                    if (k > 0) sb.append('/');
                    sb.append(String.format("%.1f", d.keypoints().get(k)));
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
