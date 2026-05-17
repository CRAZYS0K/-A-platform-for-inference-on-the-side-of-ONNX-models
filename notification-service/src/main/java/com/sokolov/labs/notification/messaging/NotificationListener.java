package com.sokolov.labs.notification.messaging;

import com.sokolov.labs.notification.email.EmailNotifier;
import com.sokolov.labs.notification.telegram.TelegramNotifier;
import com.sokolov.labs.shared.dto.NotificationEvent;
import com.sokolov.labs.shared.dto.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final EmailNotifier emailNotifier;
    private final TelegramNotifier telegramNotifier;

    public NotificationListener(EmailNotifier emailNotifier, TelegramNotifier telegramNotifier) {
        this.emailNotifier = emailNotifier;
        this.telegramNotifier = telegramNotifier;
    }

    @KafkaListener(topics = "inference.notifications",
            groupId = "${spring.kafka.consumer.group-id:onnxi-notifications}")
    public void onNotification(NotificationEvent event) {
        String subject = "ONNXI task " + event.status() + ": " + event.taskId();
        String body = buildBody(event);
        log.info("Notification for task {} status={} email={} tg={}",
                event.taskId(), event.status(),
                Boolean.TRUE.equals(event.emailEnabled()),
                Boolean.TRUE.equals(event.telegramEnabled()));

        if (Boolean.TRUE.equals(event.emailEnabled()) && event.email() != null && !event.email().isBlank()) {
            emailNotifier.send(event.email(), subject, body);
        }
        if (Boolean.TRUE.equals(event.telegramEnabled())
                && event.telegramChatId() != null && !event.telegramChatId().isBlank()) {
            telegramNotifier.send(event.telegramChatId(), subject + "\n\n" + body);
        }
    }

    private static String buildBody(NotificationEvent event) {
        StringBuilder sb = new StringBuilder()
                .append("Task: ").append(event.taskId()).append('\n')
                .append("Status: ").append(event.status()).append('\n');
        if (event.status() == TaskStatus.SUCCEEDED) {
            if (event.accuracy() != null) {
                sb.append("Accuracy: ").append(event.accuracy()).append('\n');
            }
            if (event.resultS3Key() != null) {
                sb.append("Result key: ").append(event.resultS3Key()).append('\n');
            }
        } else if (event.status() == TaskStatus.FAILED) {
            sb.append("Error: ").append(event.message()).append('\n');
        }
        return sb.toString();
    }
}
