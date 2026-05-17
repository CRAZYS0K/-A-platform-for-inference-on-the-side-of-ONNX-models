package com.sokolov.labs.worker.inference;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a YOLO label file in the format:
 *   class x_center y_center width height [kpt1_x kpt1_y [kpt1_v] ...]
 * All coordinates are normalised to [0..1].
 */
public final class YoloLabel {

    public record Annotation(int classId, double xCenter, double yCenter,
                             double width, double height, List<Double> keypoints) {
    }

    private YoloLabel() {
    }

    public static List<Annotation> parse(String text) {
        List<Annotation> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 5) {
                continue;
            }
            int cls = Integer.parseInt(parts[0]);
            double xc = Double.parseDouble(parts[1]);
            double yc = Double.parseDouble(parts[2]);
            double w = Double.parseDouble(parts[3]);
            double h = Double.parseDouble(parts[4]);
            List<Double> kpts = new ArrayList<>();
            for (int i = 5; i < parts.length; i++) {
                kpts.add(Double.parseDouble(parts[i]));
            }
            out.add(new Annotation(cls, xc, yc, w, h, kpts));
        }
        return out;
    }

    public static Detection toDetection(Annotation a, int imageWidth, int imageHeight) {
        double cx = a.xCenter() * imageWidth;
        double cy = a.yCenter() * imageHeight;
        double w = a.width() * imageWidth;
        double h = a.height() * imageHeight;
        List<Double> kpts = new ArrayList<>(a.keypoints().size());
        for (int i = 0; i + 1 < a.keypoints().size(); ) {
            kpts.add(a.keypoints().get(i) * imageWidth);
            kpts.add(a.keypoints().get(i + 1) * imageHeight);
            int stride = (a.keypoints().size() % 3 == 0) ? 3 : 2;
            if (stride == 3 && i + 2 < a.keypoints().size()) {
                kpts.add(a.keypoints().get(i + 2));
            }
            i += stride;
        }
        return new Detection(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 1.0, a.classId(), kpts);
    }
}
