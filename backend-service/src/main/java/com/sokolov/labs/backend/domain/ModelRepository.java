package com.sokolov.labs.backend.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModelRepository extends JpaRepository<Model, UUID> {

    Page<Model> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Model> findByIdAndOwnerId(UUID id, UUID ownerId);
}
