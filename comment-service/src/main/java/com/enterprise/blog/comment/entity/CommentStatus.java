package com.enterprise.blog.comment.entity;

/**
 * Moderation states.
 *
 * PENDING  — Awaiting manual moderation (default for new accounts).
 * APPROVED — Visible to all readers.
 * REJECTED — Hidden; author is notified.
 * SPAM     — Hidden and flagged for review.
 */
public enum CommentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SPAM
}
