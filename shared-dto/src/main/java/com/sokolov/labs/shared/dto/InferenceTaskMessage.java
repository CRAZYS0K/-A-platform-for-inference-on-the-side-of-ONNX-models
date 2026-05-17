package com.sokolov.labs.shared.dto;

import java.util.UUID;

public record InferenceTaskMessage(
        UUID taskId,
        UUID ownerId,
        UUID modelId,
        UUID datasetId,
        String modelS3Key,
        String datasetS3Key,
        boolean labeled
) {
}
