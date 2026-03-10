package com.enterprise.blog.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.util.Set;
import java.util.UUID;

@Data
public class BlogPostRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 300, message = "Title must be between 5 and 300 characters")
    private String title;

    @Size(max = 500, message = "Summary cannot exceed 500 characters")
    private String summary;

    @NotBlank(message = "Content is required")
    @Size(min = 50, message = "Content must be at least 50 characters")
    private String content;

    private UUID categoryId;

    private Set<String> tags;   // tag slugs

    @URL(message = "Cover image must be a valid URL")
    @Size(max = 500)
    private String coverImageUrl;
}
