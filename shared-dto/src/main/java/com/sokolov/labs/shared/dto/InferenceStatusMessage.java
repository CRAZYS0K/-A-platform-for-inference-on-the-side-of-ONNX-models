package com.sokolov.labs.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record InferenceStatusMessage(
        UUID taskId,
        UUID ownerId,
        TaskStatus status,
        int progressPct,
        String message,
        String resultS3Key,
        Double accuracy,
        Instant occurredAt
) {
}
