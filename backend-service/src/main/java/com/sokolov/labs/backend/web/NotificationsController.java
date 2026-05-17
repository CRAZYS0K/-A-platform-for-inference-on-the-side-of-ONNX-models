package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.domain.NotificationPreferences;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.NotificationPreferencesService;
import com.sokolov.labs.backend.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notifications")
public class NotificationsController {

    private final NotificationPreferencesService preferencesService;
    private final UserAccountService userAccountService;

    public NotificationsController(NotificationPreferencesService preferencesService,
                                   UserAccountService userAccountService) {
        this.preferencesService = preferencesService;
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public PrefsResponse get(@AuthenticationPrincipal Jwt jwt) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return PrefsResponse.from(preferencesService.findOrCreate(user.getId()));
    }

    @PutMapping
    public PrefsResponse update(@AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody PrefsRequest request) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return PrefsResponse.from(preferencesService.update(user.getId(),
                request.emailEnabled(), request.telegramEnabled(), request.telegramChatId()));
    }

    public record PrefsRequest(boolean emailEnabled, boolean telegramEnabled, String telegramChatId) {
    }

    public record PrefsResponse(boolean emailEnabled, boolean telegramEnabled, String telegramChatId) {
        static PrefsResponse from(NotificationPreferences p) {
            return new PrefsResponse(p.isEmailEnabled(), p.isTelegramEnabled(), p.getTelegramChatId());
        }
    }
}
