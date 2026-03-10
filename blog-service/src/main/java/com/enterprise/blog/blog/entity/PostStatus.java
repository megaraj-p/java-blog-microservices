package com.enterprise.blog.blog.entity;

/**
 * Lifecycle states for a blog post.
 *
 * DRAFT     — Saved by the author, not visible to readers.
 * IN_REVIEW — Submitted for editorial review (optional workflow step).
 * PUBLISHED — Live and visible to all readers.
 * ARCHIVED  — No longer prominently featured but still accessible by URL.
 * DELETED   — Soft-deleted; hidden from all views but retained in DB for audit.
 */
public enum PostStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    ARCHIVED,
    DELETED
}
