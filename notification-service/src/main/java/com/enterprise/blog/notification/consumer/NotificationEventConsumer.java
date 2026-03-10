package com.enterprise.blog.notification.consumer;

import com.enterprise.blog.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final EmailService emailService;

    @KafkaListener(topics = "user.registered", groupId = "notification-service")
    public void handleUserRegistered(Map<String, Object> event,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Processing event from topic: {}", topic);
        String email = String.valueOf(event.get("email"));
        String username = String.valueOf(event.get("username"));
        if (email != null && !email.isBlank()) {
            emailService.sendWelcomeEmail(email, username);
        }
    }

    @KafkaListener(topics = "blog.published", groupId = "notification-service")
    public void handleBlogPublished(Map<String, Object> event,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Processing event from topic: {}", topic);
        // In a real system, this would look up subscriber emails from user-service
        // For now, we notify the author directly
        String authorEmail = String.valueOf(event.getOrDefault("authorEmail", ""));
        if (!authorEmail.isBlank()) {
            emailService.sendBlogPublishedEmail(authorEmail, event);
        }
    }

    @KafkaListener(topics = "comment.created", groupId = "notification-service")
    public void handleCommentCreated(Map<String, Object> event,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.info("Processing event from topic: {}", topic);
        String postAuthorEmail = String.valueOf(event.getOrDefault("postAuthorEmail", ""));
        if (!postAuthorEmail.isBlank()) {
            emailService.sendNewCommentEmail(postAuthorEmail, event);
        }
    }
}
