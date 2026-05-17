package com.sokolov.labs.backend.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {

    Page<Dataset> findByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Dataset> findByIdAndOwnerId(UUID id, UUID ownerId);
}
