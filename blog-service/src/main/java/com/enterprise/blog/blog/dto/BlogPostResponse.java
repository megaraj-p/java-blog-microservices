package com.enterprise.blog.blog.dto;

import com.enterprise.blog.blog.entity.PostStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class BlogPostResponse {
    private UUID id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private UUID authorId;
    private String authorName;
    private String categoryName;
    private String categorySlug;
    private Set<String> tags;
    private PostStatus status;
    private String coverImageUrl;
    private long viewCount;
    private Integer readTimeMinutes;
    private boolean featured;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
