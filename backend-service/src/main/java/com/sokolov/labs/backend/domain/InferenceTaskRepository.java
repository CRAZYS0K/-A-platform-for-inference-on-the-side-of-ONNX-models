package com.sokolov.labs.backend.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InferenceTaskRepository extends JpaRepository<InferenceTask, UUID> {

    Page<InferenceTask> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Optional<InferenceTask> findByIdAndOwnerId(UUID id, UUID ownerId);
}
