package com.enterprise.blog.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CommentRequest {

    @NotNull(message = "Blog post ID is required")
    private UUID blogPostId;

    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 5000, message = "Comment must be between 1 and 5000 characters")
    private String content;

    /** Null for root-level comments; set for replies. */
    private UUID parentCommentId;
}
