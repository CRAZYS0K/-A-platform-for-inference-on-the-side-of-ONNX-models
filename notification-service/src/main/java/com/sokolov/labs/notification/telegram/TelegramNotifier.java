package com.sokolov.labs.notification.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final BotSender sender;
    private final boolean enabled;

    public TelegramNotifier(@Value("${notification.telegram.bot-token:}") String token) {
        this.enabled = token != null && !token.isBlank();
        this.sender = enabled ? new BotSender(token) : null;
        if (!enabled) {
            log.warn("Telegram bot token not configured — telegram notifications disabled");
        }
    }

    public void send(String chatId, String text) {
        if (!enabled) {
            log.debug("Telegram disabled, skipping message to {}", chatId);
            return;
        }
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        try {
            sender.execute(message);
            log.info("Telegram message sent to chat {}", chatId);
        } catch (TelegramApiException e) {
            log.warn("Failed to send Telegram message to {}: {}", chatId, e.toString());
        }
    }

    private static class BotSender extends DefaultAbsSender {
        private final String token;

        BotSender(String token) {
            super(new DefaultBotOptions(), token);
            this.token = token;
        }

        @Override
        public String getBotToken() {
            return token;
        }
    }
}
