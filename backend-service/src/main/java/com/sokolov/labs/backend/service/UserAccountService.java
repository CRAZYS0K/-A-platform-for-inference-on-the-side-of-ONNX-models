package com.sokolov.labs.backend.service;

import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.domain.UserAccountRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserAccountService {

    private final UserAccountRepository repository;

    public UserAccountService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserAccount findOrCreate(Jwt jwt) {
        String subject = jwt.getSubject();
        var byKc = repository.findByKcSubject(subject);
        if (byKc.isPresent()) {
            return updateFromJwt(byKc.get(), jwt);
        }
        // No row for this kc_subject. Maybe the Keycloak realm was recreated and
        // this is the same person under a fresh sub — match by email and re-bind.
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            var byEmail = repository.findByEmail(email);
            if (byEmail.isPresent()) {
                UserAccount account = byEmail.get();
                account.setKcSubject(subject);
                return updateFromJwt(account, jwt);
            }
        }
        return createFromJwt(jwt);
    }

    private UserAccount createFromJwt(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("preferred_username");
        UserAccount account = new UserAccount(
                UUID.randomUUID(),
                jwt.getSubject(),
                email,
                displayName,
                Instant.now()
        );
        return repository.save(account);
    }

    private UserAccount updateFromJwt(UserAccount existing, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("preferred_username");
        boolean changed = false;
        if (email != null && !email.equals(existing.getEmail())) {
            existing.setEmail(email);
            changed = true;
        }
        if (displayName != null && !displayName.equals(existing.getDisplayName())) {
            existing.setDisplayName(displayName);
            changed = true;
        }
        return changed ? repository.save(existing) : existing;
    }
}
