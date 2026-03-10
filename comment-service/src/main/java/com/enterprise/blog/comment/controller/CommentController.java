package com.enterprise.blog.comment.controller;

import com.enterprise.blog.comment.dto.CommentRequest;
import com.enterprise.blog.comment.dto.CommentResponse;
import com.enterprise.blog.comment.entity.CommentStatus;
import com.enterprise.blog.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<Page<CommentResponse>> getComments(
            @RequestParam UUID blogId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(commentService.getCommentsForPost(blogId, pageable));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<List<CommentResponse>> getReplies(@PathVariable UUID id) {
        return ResponseEntity.ok(commentService.getReplies(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CommentResponse> createComment(
            @Valid @RequestBody CommentRequest request,
            @RequestHeader("X-User-Id") UUID authorId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Anonymous") String authorName,
            @RequestHeader(value = "X-User-Avatar", defaultValue = "") String authorAvatar) {
        CommentResponse created = commentService.createComment(request, authorId, authorName, authorAvatar);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        boolean isAdmin = roles.contains("ADMIN");
        return ResponseEntity.ok(commentService.updateComment(id, body.get("content"), requesterId, isAdmin));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        boolean isAdmin = roles.contains("ADMIN");
        commentService.deleteComment(id, requesterId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /** Admin: approve a pending comment. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<CommentResponse> approveComment(@PathVariable UUID id) {
        return ResponseEntity.ok(commentService.moderateComment(id, CommentStatus.APPROVED));
    }

    /** Admin: reject a comment. */
    @PostMapping("/{id}/reject")
    public ResponseEntity<CommentResponse> rejectComment(@PathVariable UUID id) {
        return ResponseEntity.ok(commentService.moderateComment(id, CommentStatus.REJECTED));
    }

    /** Admin: view moderation queue. */
    @GetMapping("/moderation/queue")
    public ResponseEntity<Page<CommentResponse>> getModerationQueue(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(commentService.getPendingModerationQueue(pageable));
    }
}
