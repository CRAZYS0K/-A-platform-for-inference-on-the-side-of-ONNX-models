package com.sokolov.labs.worker.inference;

import ai.onnxruntime.OnnxValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parses YOLO11/26 ONNX outputs into a list of detections in the original-image
 * coordinate system. Auto-detects four common formats:
 *
 * <ul>
 *   <li><b>e2e detect</b>: tensor {@code [1, N, 6]} — already filtered, layout (x1,y1,x2,y2,conf,cls)</li>
 *   <li><b>e2e pose</b>: tensor {@code [1, N, K]} where K&gt;6 — same head + keypoints</li>
 *   <li><b>classic detect</b>: {@code [1, 4+nc, M]} — needs NMS</li>
 *   <li><b>classic pose</b>: {@code [1, 4+nc+kpts*3, M]} — needs NMS</li>
 * </ul>
 *
 * Letterbox parameters from {@link ImageLoader.Loaded} are applied to map coords back.
 */
public final class YoloOutputParser {

    public static final double DEFAULT_CONF = 0.25;
    public static final double DEFAULT_IOU = 0.45;

    private YoloOutputParser() {
    }

    public static List<Detection> parse(Object rawOutput, int inputW, int inputH,
                                        ImageLoader.Loaded letterbox,
                                        double confThreshold, double iouThreshold) {
        if (!(rawOutput instanceof float[][][] tensor)) {
            throw new IllegalStateException("Unsupported YOLO output rank: " + describe(rawOutput));
        }
        if (tensor.length != 1) {
            throw new IllegalStateException("Expected batch=1, got " + tensor.length);
        }
        float[][] head = tensor[0];
        int rows = head.length;
        if (rows == 0) {
            return List.of();
        }
        int cols = head[0].length;

        // e2e form: rows = max_det, cols ≥ 6, layout (x1,y1,x2,y2,conf,cls,[kpts...])
        if (cols >= 6 && cols < rows * 2) {
            return parseE2E(head, letterbox, confThreshold);
        }
        // classic form: rows = features dim, cols = anchors
        return parseClassic(head, inputW, inputH, letterbox, confThreshold, iouThreshold);
    }

    private static List<Detection> parseE2E(float[][] head, ImageLoader.Loaded lb, double confThreshold) {
        List<Detection> result = new ArrayList<>();
        for (float[] row : head) {
            double conf = row[4];
            if (conf < confThreshold) {
                continue;
            }
            double x1 = unletterboxX(row[0], lb);
            double y1 = unletterboxY(row[1], lb);
            double x2 = unletterboxX(row[2], lb);
            double y2 = unletterboxY(row[3], lb);
            int cls = (int) Math.round(row[5]);
            List<Double> kpts = new ArrayList<>();
            for (int j = 6; j + 1 < row.length; j += (row.length - 6) % 3 == 0 ? 3 : 2) {
                kpts.add(unletterboxX(row[j], lb));
                kpts.add(unletterboxY(row[j + 1], lb));
                if ((row.length - 6) % 3 == 0 && j + 2 < row.length) {
                    kpts.add((double) row[j + 2]);
                }
            }
            result.add(new Detection(x1, y1, x2, y2, conf, cls, kpts));
        }
        return result;
    }

    private static List<Detection> parseClassic(float[][] head, int inputW, int inputH,
                                                ImageLoader.Loaded lb, double conf, double iou) {
        int dim = head.length;
        int anchors = head[0].length;
        // Detect nc and kpts: assume row layout is [x,y,w,h, cls_0..cls_{nc-1}, kpts_x, kpts_y, kpts_v, ...]
        // Heuristic: if there are exactly 1 class score, it's typical for pose. Otherwise scan.
        // We pick "nc" by trying small values that yield (dim - 4 - nc) % 3 == 0 and reasonable.
        int nc = guessNumClasses(dim);
        int kptDim = dim - 4 - nc;
        int kpts = kptDim > 0 && kptDim % 3 == 0 ? kptDim / 3 : 0;

        List<Detection> candidates = new ArrayList<>();
        for (int i = 0; i < anchors; i++) {
            double cx = head[0][i];
            double cy = head[1][i];
            double w = head[2][i];
            double h = head[3][i];

            int bestClass = -1;
            double bestScore = 0;
            for (int c = 0; c < nc; c++) {
                double score = sigmoidIfNeeded(head[4 + c][i]);
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c;
                }
            }
            if (bestScore < conf) {
                continue;
            }

            double x1 = unletterboxX(cx - w / 2, lb);
            double y1 = unletterboxY(cy - h / 2, lb);
            double x2 = unletterboxX(cx + w / 2, lb);
            double y2 = unletterboxY(cy + h / 2, lb);

            List<Double> kptsList = new ArrayList<>();
            if (kpts > 0) {
                int base = 4 + nc;
                for (int k = 0; k < kpts; k++) {
                    double kx = unletterboxX(head[base + k * 3][i], lb);
                    double ky = unletterboxY(head[base + k * 3 + 1][i], lb);
                    double kv = head[base + k * 3 + 2][i];
                    kptsList.add(kx);
                    kptsList.add(ky);
                    kptsList.add(kv);
                }
            }

            candidates.add(new Detection(x1, y1, x2, y2, bestScore, bestClass, kptsList));
        }

        candidates.sort(Comparator.comparingDouble(Detection::confidence).reversed());
        return nms(candidates, iou);
    }

    private static int guessNumClasses(int dim) {
        // Likely candidates: nc=1 (single-class pose), 2 (custom), 80 (COCO detect)
        if (dim == 4 + 80) return 80;                  // detect COCO
        if ((dim - 4) % 3 == 0) return 1;              // pose with 1 class (and (dim-5)/3 keypoints)
        if ((dim - 4 - 1) % 3 == 0) return 1;
        if ((dim - 4 - 2) % 3 == 0) return 2;
        if ((dim - 4 - 80) % 3 == 0) return 80;
        return Math.max(1, dim - 4);                   // fallback: assume all classes, no keypoints
    }

    private static double sigmoidIfNeeded(double x) {
        // YOLO11/26 already applies sigmoid; assume score is in [0,1].
        // If out-of-range slipped through, clip.
        if (x < 0 || x > 1) {
            return 1.0 / (1.0 + Math.exp(-x));
        }
        return x;
    }

    private static double unletterboxX(double x, ImageLoader.Loaded lb) {
        return (x - lb.padLeft()) / lb.scale();
    }

    private static double unletterboxY(double y, ImageLoader.Loaded lb) {
        return (y - lb.padTop()) / lb.scale();
    }

    private static List<Detection> nms(List<Detection> sorted, double iouThreshold) {
        List<Detection> kept = new ArrayList<>();
        for (Detection candidate : sorted) {
            boolean suppress = false;
            for (Detection kept1 : kept) {
                if (kept1.classId() == candidate.classId() && kept1.iou(candidate) > iouThreshold) {
                    suppress = true;
                    break;
                }
            }
            if (!suppress) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static String describe(Object o) {
        if (o == null) return "null";
        return o.getClass().getSimpleName();
    }

    @SuppressWarnings("unused")
    public static Object pickValue(OnnxValue v) {
        try {
            return v.getValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read ONNX output", e);
        }
    }
}
