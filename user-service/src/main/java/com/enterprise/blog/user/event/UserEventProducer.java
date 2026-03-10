package com.enterprise.blog.user.event;

import com.enterprise.blog.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes user lifecycle events to Kafka.
 *
 * Events are published asynchronously so they do not block the
 * HTTP response thread. The Kafka producer is configured with
 * idempotence enabled and acks=all for durability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.user-registered:user.registered}")
    private String userRegisteredTopic;

    @Async
    public void publishUserRegistered(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(user.getId().toString())
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .occurredAt(Instant.now())
            .build();

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(userRegisteredTopic, user.getId().toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish UserRegisteredEvent for userId={}: {}",
                    user.getId(), ex.getMessage(), ex);
            } else {
                log.info("UserRegisteredEvent published: topic={}, partition={}, offset={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }

    // ── Inner Event DTO ──────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRegisteredEvent {
        private String eventId;
        private String userId;
        private String username;
        private String email;
        private String displayName;
        private Instant occurredAt;
    }
}
