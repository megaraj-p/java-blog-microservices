package com.enterprise.blog.analytics.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blog_views", indexes = {
        @Index(name = "idx_view_post_id", columnList = "blog_post_id"),
        @Index(name = "idx_view_viewer_id", columnList = "viewer_user_id"),
        @Index(name = "idx_view_timestamp", columnList = "viewed_at"),
        @Index(name = "idx_view_post_date", columnList = "blog_post_id, viewed_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blog_post_id", nullable = false)
    private String blogPostId;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "viewer_user_id")
    private String viewerUserId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
