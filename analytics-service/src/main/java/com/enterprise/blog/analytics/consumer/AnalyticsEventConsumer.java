package com.enterprise.blog.analytics.consumer;

import com.enterprise.blog.analytics.entity.BlogView;
import com.enterprise.blog.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsService analyticsService;

    @KafkaListener(topics = "blog.viewed", groupId = "analytics-service")
    public void handleBlogViewed(Map<String, Object> event) {
        try {
            BlogView view = BlogView.builder()
                    .blogPostId(String.valueOf(event.get("postId")))
                    .authorId(String.valueOf(event.getOrDefault("authorId", "")))
                    .viewerUserId(String.valueOf(event.getOrDefault("viewerUserId", null)))
                    .ipAddress(String.valueOf(event.getOrDefault("ipAddress", "")))
                    .userAgent(String.valueOf(event.getOrDefault("userAgent", "")))
                    .sessionId(String.valueOf(event.getOrDefault("sessionId", "")))
                    .viewedAt(LocalDateTime.now())
                    .build();
            analyticsService.recordView(view);
        } catch (Exception e) {
            log.error("Failed to record blog view event: {}", event, e);
        }
    }
}
