package com.sokolov.labs.backend.service;

import com.sokolov.labs.backend.domain.NotificationPreferences;
import com.sokolov.labs.backend.domain.NotificationPreferencesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationPreferencesService {

    private final NotificationPreferencesRepository repository;

    public NotificationPreferencesService(NotificationPreferencesRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationPreferences findOrCreate(UUID userId) {
        return repository.findById(userId)
                .orElseGet(() -> repository.save(new NotificationPreferences(userId)));
    }

    @Transactional
    public NotificationPreferences update(UUID userId, boolean emailEnabled,
                                          boolean telegramEnabled, String telegramChatId) {
        NotificationPreferences prefs = findOrCreate(userId);
        prefs.setEmailEnabled(emailEnabled);
        prefs.setTelegramEnabled(telegramEnabled);
        prefs.setTelegramChatId(telegramChatId);
        return repository.save(prefs);
    }
}
