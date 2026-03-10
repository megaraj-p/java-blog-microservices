package com.enterprise.blog.comment.dto;

import com.enterprise.blog.comment.entity.CommentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CommentResponse {
    private UUID id;
    private UUID blogPostId;
    private UUID authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private UUID parentId;
    private List<CommentResponse> replies;
    private CommentStatus status;
    private boolean edited;
    private int likeCount;
    private int replyCount;
    private Instant createdAt;
    private Instant updatedAt;
}
