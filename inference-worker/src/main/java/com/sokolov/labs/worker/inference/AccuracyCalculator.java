package com.sokolov.labs.worker.inference;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes evaluation metrics for YOLO detection / pose models.
 * <ul>
 *   <li>For detection: mean per-image precision (matched detections at IoU&gt;=0.5).</li>
 *   <li>For pose: PCK (% keypoints within a pixel threshold of ground truth keypoints
 *   of the IoU-matched detection).</li>
 * </ul>
 */
public final class AccuracyCalculator {

    public static final double IOU_THRESHOLD = 0.5;
    public static final double KEYPOINT_PIXEL_THRESHOLD = 5.0;

    private AccuracyCalculator() {
    }

    public record FrameScore(double detectionRecall, double pck, int keypointPairs) {
    }

    public static FrameScore evaluate(List<Detection> predicted, List<Detection> truth) {
        if (truth.isEmpty()) {
            return new FrameScore(Double.NaN, Double.NaN, 0);
        }

        // Greedy IoU matching: each gt → best unmatched prediction
        Set<Integer> usedPred = new HashSet<>();
        int matchedGt = 0;
        int kptHits = 0;
        int kptTotal = 0;

        for (Detection gt : truth) {
            int bestIdx = -1;
            double bestIou = IOU_THRESHOLD;
            for (int i = 0; i < predicted.size(); i++) {
                if (usedPred.contains(i)) continue;
                Detection p = predicted.get(i);
                double iou = gt.iou(p);
                if (iou > bestIou) {
                    bestIou = iou;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                matchedGt++;
                usedPred.add(bestIdx);
                Detection p = predicted.get(bestIdx);
                int pairs = Math.min(p.keypoints().size() / 2, gt.keypoints().size() / 2);
                int stridePred = (p.keypoints().size() % 3 == 0) ? 3 : 2;
                int strideGt = (gt.keypoints().size() % 3 == 0) ? 3 : 2;
                for (int k = 0; k < pairs; k++) {
                    double pxIdx = k * stridePred;
                    double gxIdx = k * strideGt;
                    if (pxIdx + 1 >= p.keypoints().size() || gxIdx + 1 >= gt.keypoints().size()) break;
                    double dx = p.keypoints().get((int) pxIdx) - gt.keypoints().get((int) gxIdx);
                    double dy = p.keypoints().get((int) pxIdx + 1) - gt.keypoints().get((int) gxIdx + 1);
                    double dist = Math.hypot(dx, dy);
                    kptTotal++;
                    if (dist <= KEYPOINT_PIXEL_THRESHOLD) {
                        kptHits++;
                    }
                }
            }
        }
        double recall = (double) matchedGt / truth.size();
        double pck = kptTotal > 0 ? (double) kptHits / kptTotal : Double.NaN;
        return new FrameScore(recall, pck, kptTotal);
    }
}
