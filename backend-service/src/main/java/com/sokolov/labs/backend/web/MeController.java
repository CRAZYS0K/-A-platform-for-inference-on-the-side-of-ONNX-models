package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.UserAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserAccountService userAccountService;

    public MeController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserAccount account = userAccountService.findOrCreate(jwt);
        return new MeResponse(
                account.getId(),
                account.getKcSubject(),
                account.getEmail(),
                account.getDisplayName(),
                account.getCreatedAt()
        );
    }

    public record MeResponse(UUID id, String subject, String email, String displayName, Instant createdAt) {
    }
}
