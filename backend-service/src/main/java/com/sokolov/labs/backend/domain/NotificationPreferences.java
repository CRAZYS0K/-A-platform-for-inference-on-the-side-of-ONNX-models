package com.sokolov.labs.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_prefs")
public class NotificationPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "telegram_enabled", nullable = false)
    private boolean telegramEnabled = false;

    @Column(name = "telegram_chat_id", length = 64)
    private String telegramChatId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreferences() {
    }

    public NotificationPreferences(UUID userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isTelegramEnabled() { return telegramEnabled; }
    public String getTelegramChatId() { return telegramChatId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
        this.updatedAt = Instant.now();
    }

    public void setTelegramEnabled(boolean telegramEnabled) {
        this.telegramEnabled = telegramEnabled;
        this.updatedAt = Instant.now();
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
        this.updatedAt = Instant.now();
    }
}
