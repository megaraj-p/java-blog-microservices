package com.enterprise.blog.blog.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes blog lifecycle events to Kafka.
 *
 * Consumers:
 *  blog.published → Search Service (index), Notification Service (email followers)
 *  blog.deleted   → Search Service (remove from index)
 *  blog.viewed    → Analytics Service (track view)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.blog-published:blog.published}")
    private String blogPublishedTopic;

    @Value("${kafka.topics.blog-deleted:blog.deleted}")
    private String blogDeletedTopic;

    @Value("${kafka.topics.blog-viewed:blog.viewed}")
    private String blogViewedTopic;

    @Async
    public void publishBlogPublished(UUID blogId, UUID authorId, String authorName,
                                     String title, String slug, String summary,
                                     String content, String categorySlug, Set<String> tags) {
        BlogPublishedEvent event = BlogPublishedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .blogId(blogId.toString())
            .authorId(authorId.toString())
            .authorName(authorName)
            .title(title)
            .slug(slug)
            .summary(summary)
            .content(content)
            .categorySlug(categorySlug)
            .tags(tags != null ? List.copyOf(tags) : List.of())
            .occurredAt(Instant.now())
            .build();

        sendEvent(blogPublishedTopic, blogId.toString(), event);
    }

    @Async
    public void publishBlogDeleted(UUID blogId) {
        BlogDeletedEvent event = BlogDeletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .blogId(blogId.toString())
            .occurredAt(Instant.now())
            .build();

        sendEvent(blogDeletedTopic, blogId.toString(), event);
    }

    @Async
    public void publishBlogViewed(UUID blogId, String viewerUserId, String ipAddress) {
        BlogViewedEvent event = BlogViewedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .blogId(blogId.toString())
            .viewerUserId(viewerUserId)
            .ipAddress(ipAddress)
            .occurredAt(Instant.now())
            .build();

        sendEvent(blogViewedTopic, blogId.toString(), event);
    }

    private void sendEvent(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic={}: {}", topic, ex.getMessage(), ex);
                } else {
                    log.debug("Event published: topic={}, partition={}, offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }

    // ── Event DTOs ───────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BlogPublishedEvent {
        private String eventId;
        private String blogId;
        private String authorId;
        private String authorName;
        private String title;
        private String slug;
        private String summary;
        private String content;
        private String categorySlug;
        private List<String> tags;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BlogDeletedEvent {
        private String eventId;
        private String blogId;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BlogViewedEvent {
        private String eventId;
        private String blogId;
        private String viewerUserId;
        private String ipAddress;
        private Instant occurredAt;
    }
}
