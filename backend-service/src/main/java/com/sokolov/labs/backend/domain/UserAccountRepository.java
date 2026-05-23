package com.sokolov.labs.backend.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByKcSubject(String kcSubject);

    Optional<UserAccount> findByEmail(String email);
}
