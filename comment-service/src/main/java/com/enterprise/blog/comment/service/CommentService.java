package com.enterprise.blog.comment.service;

import com.enterprise.blog.comment.dto.CommentRequest;
import com.enterprise.blog.comment.dto.CommentResponse;
import com.enterprise.blog.comment.entity.Comment;
import com.enterprise.blog.comment.entity.CommentStatus;
import com.enterprise.blog.comment.event.CommentEventProducer;
import com.enterprise.blog.comment.exception.ForbiddenException;
import com.enterprise.blog.comment.exception.ResourceNotFoundException;
import com.enterprise.blog.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentEventProducer commentEventProducer;

    public Page<CommentResponse> getCommentsForPost(UUID blogPostId, Pageable pageable) {
        return commentRepository
            .findRootComments(blogPostId, CommentStatus.APPROVED, pageable)
            .map(this::toResponse);
    }

    public List<CommentResponse> getReplies(UUID parentId) {
        Comment parent = commentRepository.findById(parentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + parentId));
        return commentRepository.findReplies(parent.getId(), CommentStatus.APPROVED)
            .stream().map(this::toResponse).toList();
    }

    public Page<CommentResponse> getPendingModerationQueue(Pageable pageable) {
        return commentRepository
            .findByStatusOrderByCreatedAtAsc(CommentStatus.PENDING, pageable)
            .map(this::toResponse);
    }

    @Transactional
    public CommentResponse createComment(CommentRequest request, UUID authorId,
                                         String authorName, String authorAvatar) {
        Comment comment = Comment.builder()
            .blogPostId(request.getBlogPostId())
            .authorId(authorId)
            .authorName(authorName)
            .authorAvatar(authorAvatar)
            .content(request.getContent())
            .status(CommentStatus.APPROVED) // Trusted users go live immediately
            .build();

        if (request.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(request.getParentCommentId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            comment.setParent(parent);
        }

        Comment saved = commentRepository.save(comment);

        // Async event → Notification Service (notifies post author of new comment)
        commentEventProducer.publishCommentCreated(saved);

        log.info("Comment created: id={}, blogPostId={}", saved.getId(), saved.getBlogPostId());
        return toResponse(saved);
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId, String newContent, UUID requesterId, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!isAdmin && !comment.getAuthorId().equals(requesterId)) {
            throw new ForbiddenException("You do not have permission to edit this comment");
        }

        comment.setContent(newContent);
        comment.setEdited(true);
        return toResponse(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID requesterId, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!isAdmin && !comment.getAuthorId().equals(requesterId)) {
            throw new ForbiddenException("You do not have permission to delete this comment");
        }

        commentRepository.delete(comment);
        log.info("Comment deleted: id={}", commentId);
    }

    @Transactional
    public CommentResponse moderateComment(UUID commentId, CommentStatus newStatus) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        comment.setStatus(newStatus);
        return toResponse(commentRepository.save(comment));
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponse.builder()
            .id(comment.getId())
            .blogPostId(comment.getBlogPostId())
            .authorId(comment.getAuthorId())
            .authorName(comment.getAuthorName())
            .authorAvatar(comment.getAuthorAvatar())
            .content(comment.getContent())
            .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
            .status(comment.getStatus())
            .edited(comment.isEdited())
            .likeCount(comment.getLikeCount())
            .replyCount(comment.getReplies() != null ? comment.getReplies().size() : 0)
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }
}
