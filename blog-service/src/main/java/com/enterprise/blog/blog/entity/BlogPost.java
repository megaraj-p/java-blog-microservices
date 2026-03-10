package com.enterprise.blog.blog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Blog post entity.
 *
 * Supports full post lifecycle: DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED.
 * Content is stored as Markdown; the rendering layer converts to HTML.
 * Tags and Categories form a many-to-many and many-to-one relationship respectively.
 *
 * authorId references the User Service (cross-service reference — no FK constraint).
 */
@Entity
@Table(name = "blog_posts", indexes = {
    @Index(name = "idx_posts_author_id",  columnList = "author_id"),
    @Index(name = "idx_posts_status",     columnList = "status"),
    @Index(name = "idx_posts_slug",       columnList = "slug", unique = true),
    @Index(name = "idx_posts_published",  columnList = "published_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    /** URL-friendly identifier, e.g. "how-to-build-microservices". */
    @Column(name = "slug", nullable = false, unique = true, length = 350)
    private String slug;

    @Column(name = "summary", length = 500)
    private String summary;

    /** Full Markdown content — stored as TEXT for unlimited size. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Reference to the author in user-service — not a FK (cross-service). */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Cached author display name to avoid cross-service calls on read. */
    @Column(name = "author_name", length = 100)
    private String authorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "post_tags",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private long viewCount = 0L;

    @Column(name = "read_time_minutes")
    private Integer readTimeMinutes;

    @Column(name = "featured", nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Convenience methods ──────────────────────────────

    public void addTag(Tag tag) {
        tags.add(tag);
        tag.getPosts().add(this);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.getPosts().remove(this);
    }

    public boolean isOwnedBy(UUID userId) {
        return authorId.equals(userId);
    }
}
