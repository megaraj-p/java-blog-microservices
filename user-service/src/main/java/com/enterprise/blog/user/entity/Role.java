package com.enterprise.blog.user.entity;

/**
 * RBAC roles assigned to users.
 *
 * Permissions:
 *  READER — browse and read published content
 *  AUTHOR — create and manage their own blog posts + READER perms
 *  ADMIN  — full access: user management, moderation, analytics + AUTHOR perms
 */
public enum Role {
    READER,
    AUTHOR,
    ADMIN
}
