package com.enterprise.blog.comment.event;

import com.enterprise.blog.comment.entity.Comment;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.comment-created:comment.created}")
    private String commentCreatedTopic;

    @Async
    public void publishCommentCreated(Comment comment) {
        CommentCreatedEvent event = CommentCreatedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .commentId(comment.getId().toString())
            .blogPostId(comment.getBlogPostId().toString())
            .authorId(comment.getAuthorId().toString())
            .authorName(comment.getAuthorName())
            .contentPreview(comment.getContent().length() > 200
                ? comment.getContent().substring(0, 200) + "..." : comment.getContent())
            .parentCommentId(comment.getParent() != null ? comment.getParent().getId().toString() : null)
            .occurredAt(Instant.now())
            .build();

        kafkaTemplate.send(commentCreatedTopic, comment.getBlogPostId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish CommentCreatedEvent: {}", ex.getMessage());
                } else {
                    log.debug("CommentCreatedEvent published for commentId={}", comment.getId());
                }
            });
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CommentCreatedEvent {
        private String eventId;
        private String commentId;
        private String blogPostId;
        private String authorId;
        private String authorName;
        private String contentPreview;
        private String parentCommentId;
        private Instant occurredAt;
    }
}
