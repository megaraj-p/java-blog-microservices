package com.enterprise.blog.comment.repository;

import com.enterprise.blog.comment.entity.Comment;
import com.enterprise.blog.comment.entity.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Top-level comments only (parent is null) for a blog post. */
    @Query("SELECT c FROM Comment c WHERE c.blogPostId = :blogPostId AND c.parent IS NULL AND c.status = :status ORDER BY c.createdAt ASC")
    Page<Comment> findRootComments(
        @Param("blogPostId") UUID blogPostId,
        @Param("status") CommentStatus status,
        Pageable pageable);

    /** Replies to a specific comment. */
    @Query("SELECT c FROM Comment c WHERE c.parent.id = :parentId AND c.status = :status ORDER BY c.createdAt ASC")
    List<Comment> findReplies(
        @Param("parentId") UUID parentId,
        @Param("status") CommentStatus status);

    /** All comments by a user across all posts. */
    Page<Comment> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    /** Pending moderation queue (Admin). */
    Page<Comment> findByStatusOrderByCreatedAtAsc(CommentStatus status, Pageable pageable);

    long countByBlogPostIdAndStatus(UUID blogPostId, CommentStatus status);
}
