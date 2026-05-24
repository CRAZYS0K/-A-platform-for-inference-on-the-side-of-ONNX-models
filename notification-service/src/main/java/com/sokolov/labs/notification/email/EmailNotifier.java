package com.sokolov.labs.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotifier(JavaMailSender mailSender,
                         @Value("${notification.email.from:onnxi@localhost}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.info("Email sent to {} ({})", to, subject);
        } catch (Exception e) {
            // Не пробрасываем дальше: Kafka-консьюмер иначе уйдёт в бесконечный
            // retry для постоянных ошибок (неверный адрес). Логируем со stack trace
            // на ERROR — это deliberate degradation, не молчаливое проглатывание.
            log.error("Failed to send email to {} (subject={})", to, subject, e);
        }
    }
}
