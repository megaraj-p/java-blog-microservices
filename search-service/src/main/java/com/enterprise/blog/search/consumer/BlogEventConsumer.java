package com.enterprise.blog.search.consumer;

import com.enterprise.blog.search.document.BlogDocument;
import com.enterprise.blog.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlogEventConsumer {

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "blog.published", groupId = "search-service")
    public void handleBlogPublished(Map<String, Object> event) {
        try {
            log.info("Received blog.published event for post: {}", event.get("postId"));
            BlogDocument document = BlogDocument.builder()
                    .id(String.valueOf(event.get("postId")))
                    .title(String.valueOf(event.get("title")))
                    .slug(String.valueOf(event.get("slug")))
                    .excerpt(String.valueOf(event.getOrDefault("excerpt", "")))
                    .content(String.valueOf(event.getOrDefault("content", "")))
                    .authorId(String.valueOf(event.get("authorId")))
                    .authorName(String.valueOf(event.getOrDefault("authorName", "")))
                    .category(String.valueOf(event.getOrDefault("category", "")))
                    .tags(objectMapper.convertValue(event.getOrDefault("tags", List.of()), List.class))
                    .readTimeMinutes((Integer) event.getOrDefault("readTimeMinutes", 0))
                    .viewCount(((Number) event.getOrDefault("viewCount", 0L)).longValue())
                    .publishedAt(LocalDateTime.now())
                    .build();
            searchService.indexPost(document);
        } catch (Exception e) {
            log.error("Failed to index blog post from event: {}", event.get("postId"), e);
        }
    }

    @KafkaListener(topics = "blog.deleted", groupId = "search-service")
    public void handleBlogDeleted(Map<String, Object> event) {
        String postId = String.valueOf(event.get("postId"));
        log.info("Received blog.deleted event for post: {}", postId);
        searchService.deletePost(postId);
    }
}
