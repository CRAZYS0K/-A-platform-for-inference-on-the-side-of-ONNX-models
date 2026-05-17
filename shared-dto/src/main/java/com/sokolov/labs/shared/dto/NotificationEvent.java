package com.sokolov.labs.shared.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationEvent(
        UUID taskId,
        UUID ownerId,
        TaskStatus status,
        Double accuracy,
        String message,
        String resultS3Key,
        String email,
        Boolean emailEnabled,
        String telegramChatId,
        Boolean telegramEnabled,
        Instant occurredAt
) {
}
