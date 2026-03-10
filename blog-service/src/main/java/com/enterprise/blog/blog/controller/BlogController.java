package com.enterprise.blog.blog.controller;

import com.enterprise.blog.blog.dto.BlogPostRequest;
import com.enterprise.blog.blog.dto.BlogPostResponse;
import com.enterprise.blog.blog.entity.Category;
import com.enterprise.blog.blog.entity.Tag;
import com.enterprise.blog.blog.repository.CategoryRepository;
import com.enterprise.blog.blog.repository.TagRepository;
import com.enterprise.blog.blog.service.BlogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Blog REST API.
 *
 * Authenticated context is passed via gateway-injected headers:
 *  X-User-Id    — requester UUID
 *  X-User-Roles — comma-separated roles (ADMIN, AUTHOR, READER)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    // ── Public Read Endpoints ────────────────────────────

    @GetMapping("/blogs")
    public ResponseEntity<Page<BlogPostResponse>> listPosts(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<BlogPostResponse> result;
        if (tag != null) {
            result = blogService.listByTag(tag, pageable);
        } else if (category != null) {
            result = blogService.listByCategory(category, pageable);
        } else {
            result = blogService.listPublished(pageable);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/blogs/{idOrSlug}")
    public ResponseEntity<BlogPostResponse> getPost(
            @PathVariable String idOrSlug,
            HttpServletRequest request) {

        BlogPostResponse post;
        try {
            UUID id = UUID.fromString(idOrSlug);
            post = blogService.getById(id);
        } catch (IllegalArgumentException ex) {
            post = blogService.getBySlug(idOrSlug);
        }

        // Track view asynchronously
        String viewerUserId = request.getHeader("X-User-Id");
        String ipAddress    = getClientIp(request);
        blogService.trackView(post.getId(), viewerUserId, ipAddress);

        return ResponseEntity.ok(post);
    }

    @GetMapping("/blogs/my")
    public ResponseEntity<Page<BlogPostResponse>> getMyPosts(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(blogService.listByAuthor(userId, pageable));
    }

    // ── Authenticated Write Endpoints ────────────────────

    @PostMapping("/blogs")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<BlogPostResponse> createPost(
            @Valid @RequestBody BlogPostRequest request,
            @RequestHeader("X-User-Id") UUID authorId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Unknown") String authorName) {
        BlogPostResponse created = blogService.createPost(request, authorId, authorName);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/blogs/{id}")
    public ResponseEntity<BlogPostResponse> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody BlogPostRequest request,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        boolean isAdmin = roles.contains("ADMIN");
        return ResponseEntity.ok(blogService.updatePost(id, request, requesterId, isAdmin));
    }

    @PostMapping("/blogs/{id}/publish")
    public ResponseEntity<BlogPostResponse> publishPost(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        boolean isAdmin = roles.contains("ADMIN");
        return ResponseEntity.ok(blogService.publishPost(id, requesterId, isAdmin));
    }

    @PostMapping("/blogs/{id}/archive")
    public ResponseEntity<BlogPostResponse> archivePost(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        boolean isAdmin = roles.contains("ADMIN");
        return ResponseEntity.ok(blogService.archivePost(id, requesterId, isAdmin));
    }

    @DeleteMapping("/blogs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {
        boolean isAdmin = roles.contains("ADMIN");
        blogService.deletePost(id, requesterId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    // ── Categories & Tags ────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAllByOrderByDisplayOrderAsc());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<Tag>> getTags() {
        return ResponseEntity.ok(tagRepository.findAll());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
