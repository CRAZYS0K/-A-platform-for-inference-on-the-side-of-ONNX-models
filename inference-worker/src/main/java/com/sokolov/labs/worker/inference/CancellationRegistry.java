package com.sokolov.labs.worker.inference;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory set of task ids that have been cancelled by the user. The set is
 * populated by {@code TaskCancellationListener} from Kafka and consulted by
 * {@code InferenceRunner} between samples to allow cooperative interruption.
 *
 * The set may grow over time (we never clear entries because a worker that
 * sees a cancelled task again should still skip it). For long-running deployments
 * this could be capped with a bounded cache; for current use it's fine.
 */
@Component
public class CancellationRegistry {

    private final Set<UUID> cancelled = ConcurrentHashMap.newKeySet();

    public void markCancelled(UUID taskId) {
        cancelled.add(taskId);
    }

    public boolean isCancelled(UUID taskId) {
        return cancelled.contains(taskId);
    }
}
