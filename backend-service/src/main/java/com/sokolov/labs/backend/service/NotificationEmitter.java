package com.sokolov.labs.backend.service;

import com.sokolov.labs.backend.domain.NotificationPreferences;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.domain.UserAccountRepository;
import com.sokolov.labs.backend.messaging.KafkaTopics;
import com.sokolov.labs.shared.dto.InferenceStatusMessage;
import com.sokolov.labs.shared.dto.NotificationEvent;
import com.sokolov.labs.shared.dto.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Component
public class NotificationEmitter {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmitter.class);
    private static final Set<TaskStatus> NOTIFIABLE = EnumSet.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED);

    private final UserAccountRepository userAccountRepository;
    private final NotificationPreferencesService preferencesService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationEmitter(UserAccountRepository userAccountRepository,
                               NotificationPreferencesService preferencesService,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.userAccountRepository = userAccountRepository;
        this.preferencesService = preferencesService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void emitIfFinal(InferenceStatusMessage status) {
        if (!NOTIFIABLE.contains(status.status())) {
            return;
        }
        UserAccount user = userAccountRepository.findById(status.ownerId()).orElse(null);
        if (user == null) {
            log.warn("Notification skipped: user {} not found", status.ownerId());
            return;
        }
        NotificationPreferences prefs = preferencesService.findOrCreate(user.getId());

        NotificationEvent event = new NotificationEvent(
                status.taskId(), user.getId(), status.status(),
                status.accuracy(), status.message(), status.resultS3Key(),
                user.getEmail(), prefs.isEmailEnabled(),
                prefs.getTelegramChatId(), prefs.isTelegramEnabled(),
                Instant.now());
        kafkaTemplate.send(KafkaTopics.INFERENCE_NOTIFICATIONS, status.taskId().toString(), event);
        log.debug("Emitted notification for task {} (email={}, tg={})",
                status.taskId(), prefs.isEmailEnabled(), prefs.isTelegramEnabled());
    }
}
