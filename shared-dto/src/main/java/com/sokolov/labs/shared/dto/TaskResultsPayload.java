package com.sokolov.labs.shared.dto;

import java.util.List;

/**
 * Persisted result of an inference task. Stored as JSON in object storage
 * under {@code results/<ownerId>/<taskId>/results.json}.
 */
public record TaskResultsPayload(
        String modelInputShape,
        boolean labeled,
        Double recall,
        Double pck,
        List<ImageResult> items
) {
    public record ImageResult(
            String filename,
            String imageObjectKey,
            int origWidth,
            int origHeight,
            List<Detection> detections,
            List<Detection> truth
    ) {
    }

    public record Detection(
            double x1, double y1, double x2, double y2,
            double confidence, int classId,
            List<Double> keypoints
    ) {
    }
}
