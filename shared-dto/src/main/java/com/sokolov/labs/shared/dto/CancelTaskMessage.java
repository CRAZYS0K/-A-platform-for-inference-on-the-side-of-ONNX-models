package com.sokolov.labs.shared.dto;

import java.util.UUID;

/**
 * Published on {@code inference.tasks.cancel} when a user asks to stop a task.
 * Workers maintain an in-memory set of cancelled task ids and break out of the
 * inference loop at the next sample boundary.
 */
public record CancelTaskMessage(
        UUID taskId,
        UUID ownerId
) {
}
