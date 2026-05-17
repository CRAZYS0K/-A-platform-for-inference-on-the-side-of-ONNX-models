package com.sokolov.labs.worker.inference;

import java.util.List;

/**
 * One detected object in the original (pre-letterbox) image coordinate system.
 * For pose models {@code keypoints} contains {x, y[, v]} triplets; otherwise empty.
 */
public record Detection(double x1, double y1, double x2, double y2,
                        double confidence, int classId,
                        List<Double> keypoints) {

    public double cx() { return (x1 + x2) / 2.0; }
    public double cy() { return (y1 + y2) / 2.0; }
    public double w() { return x2 - x1; }
    public double h() { return y2 - y1; }

    public double iou(Detection other) {
        double interX1 = Math.max(x1, other.x1);
        double interY1 = Math.max(y1, other.y1);
        double interX2 = Math.min(x2, other.x2);
        double interY2 = Math.min(y2, other.y2);
        double interW = Math.max(0, interX2 - interX1);
        double interH = Math.max(0, interY2 - interY1);
        double interArea = interW * interH;
        double union = w() * h() + other.w() * other.h() - interArea;
        return union > 0 ? interArea / union : 0;
    }
}
