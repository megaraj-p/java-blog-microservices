package com.enterprise.blog.blog.service;

import com.enterprise.blog.blog.dto.BlogPostRequest;
import com.enterprise.blog.blog.dto.BlogPostResponse;
import com.enterprise.blog.blog.entity.BlogPost;
import com.enterprise.blog.blog.entity.Category;
import com.enterprise.blog.blog.entity.PostStatus;
import com.enterprise.blog.blog.entity.Tag;
import com.enterprise.blog.blog.event.BlogEventProducer;
import com.enterprise.blog.blog.exception.ForbiddenException;
import com.enterprise.blog.blog.exception.ResourceNotFoundException;
import com.enterprise.blog.blog.repository.BlogPostRepository;
import com.enterprise.blog.blog.repository.CategoryRepository;
import com.enterprise.blog.blog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogService {

    private final BlogPostRepository blogPostRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final BlogEventProducer blogEventProducer;

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    // ── List / Read ──────────────────────────────────────

    public Page<BlogPostResponse> listPublished(Pageable pageable) {
        return blogPostRepository
            .findByStatusOrderByPublishedAtDesc(PostStatus.PUBLISHED, pageable)
            .map(this::toResponse);
    }

    public Page<BlogPostResponse> listByTag(String tagSlug, Pageable pageable) {
        return blogPostRepository
            .findByStatusAndTagSlug(PostStatus.PUBLISHED, tagSlug, pageable)
            .map(this::toResponse);
    }

    public Page<BlogPostResponse> listByCategory(String categorySlug, Pageable pageable) {
        return blogPostRepository
            .findByStatusAndCategorySlug(PostStatus.PUBLISHED, categorySlug, pageable)
            .map(this::toResponse);
    }

    public Page<BlogPostResponse> listByAuthor(UUID authorId, Pageable pageable) {
        return blogPostRepository
            .findByAuthorIdOrderByCreatedAtDesc(authorId, pageable)
            .map(this::toResponse);
    }

    @Cacheable(value = "blog-posts", key = "#slug")
    public BlogPostResponse getBySlug(String slug) {
        BlogPost post = blogPostRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("Blog post not found: " + slug));
        return toResponse(post);
    }

    @Cacheable(value = "blog-posts", key = "#id")
    public BlogPostResponse getById(UUID id) {
        BlogPost post = blogPostRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Blog post not found: " + id));
        return toResponse(post);
    }

    // ── Create / Update / Delete ─────────────────────────

    @Transactional
    public BlogPostResponse createPost(BlogPostRequest request, UUID authorId, String authorName) {
        String slug = generateUniqueSlug(request.getTitle());

        BlogPost post = BlogPost.builder()
            .title(request.getTitle())
            .slug(slug)
            .summary(request.getSummary())
            .content(request.getContent())
            .authorId(authorId)
            .authorName(authorName)
            .coverImageUrl(request.getCoverImageUrl())
            .status(PostStatus.DRAFT)
            .readTimeMinutes(estimateReadTime(request.getContent()))
            .build();

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));
            post.setCategory(category);
        }

        if (request.getTags() != null) {
            Set<Tag> tags = resolveTags(request.getTags());
            tags.forEach(post::addTag);
        }

        BlogPost saved = blogPostRepository.save(post);
        log.info("Blog post created: id={}, author={}", saved.getId(), authorId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "blog-posts", key = "#postId")
    public BlogPostResponse updatePost(UUID postId, BlogPostRequest request, UUID requesterId, boolean isAdmin) {
        BlogPost post = blogPostRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Blog post not found: " + postId));

        if (!isAdmin && !post.isOwnedBy(requesterId)) {
            throw new ForbiddenException("You do not have permission to edit this post");
        }

        post.setTitle(request.getTitle());
        post.setSummary(request.getSummary());
        post.setContent(request.getContent());
        post.setCoverImageUrl(request.getCoverImageUrl());
        post.setReadTimeMinutes(estimateReadTime(request.getContent()));

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            post.setCategory(category);
        }

        if (request.getTags() != null) {
            post.getTags().clear();
            resolveTags(request.getTags()).forEach(post::addTag);
        }

        return toResponse(blogPostRepository.save(post));
    }

    // ── Publish Workflow ─────────────────────────────────

    @Transactional
    @CacheEvict(value = "blog-posts", allEntries = true)
    public BlogPostResponse publishPost(UUID postId, UUID requesterId, boolean isAdmin) {
        BlogPost post = blogPostRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Blog post not found: " + postId));

        if (!isAdmin && !post.isOwnedBy(requesterId)) {
            throw new ForbiddenException("You do not have permission to publish this post");
        }
        if (post.getStatus() == PostStatus.PUBLISHED) {
            return toResponse(post); // Idempotent
        }

        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(Instant.now());
        BlogPost saved = blogPostRepository.save(post);

        // Async Kafka event — triggers search indexing + notifications
        blogEventProducer.publishBlogPublished(
            saved.getId(), saved.getAuthorId(), saved.getAuthorName(),
            saved.getTitle(), saved.getSlug(), saved.getSummary(), saved.getContent(),
            saved.getCategory() != null ? saved.getCategory().getSlug() : null,
            saved.getTags().stream().map(Tag::getSlug).collect(Collectors.toSet())
        );

        log.info("Blog post published: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = "blog-posts", key = "#postId")
    public BlogPostResponse archivePost(UUID postId, UUID requesterId, boolean isAdmin) {
        BlogPost post = blogPostRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Blog post not found: " + postId));

        if (!isAdmin && !post.isOwnedBy(requesterId)) {
            throw new ForbiddenException("You do not have permission to archive this post");
        }

        post.setStatus(PostStatus.ARCHIVED);
        return toResponse(blogPostRepository.save(post));
    }

    @Transactional
    @CacheEvict(value = "blog-posts", key = "#postId")
    public void deletePost(UUID postId, UUID requesterId, boolean isAdmin) {
        BlogPost post = blogPostRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Blog post not found: " + postId));

        if (!isAdmin && !post.isOwnedBy(requesterId)) {
            throw new ForbiddenException("You do not have permission to delete this post");
        }

        // Soft delete
        post.setStatus(PostStatus.DELETED);
        blogPostRepository.save(post);
        blogEventProducer.publishBlogDeleted(postId);
        log.info("Blog post deleted (soft): id={}", postId);
    }

    // ── View Tracking ────────────────────────────────────

    @Transactional
    public void trackView(UUID postId, String viewerUserId, String ipAddress) {
        blogPostRepository.incrementViewCount(postId);
        blogEventProducer.publishBlogViewed(postId, viewerUserId, ipAddress);
    }

    // ── Helpers ──────────────────────────────────────────

    private String generateUniqueSlug(String title) {
        String baseSlug = toSlug(title);
        String slug = baseSlug;
        int counter = 1;
        while (blogPostRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private String toSlug(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return NON_ALPHANUMERIC
            .matcher(normalized.toLowerCase(Locale.ENGLISH).replaceAll("[^\\p{ASCII}]", ""))
            .replaceAll("-")
            .replaceAll("^-+|-+$", ""); // trim leading/trailing dashes
    }

    private Set<Tag> resolveTags(Set<String> tagSlugs) {
        return tagSlugs.stream()
            .map(slug -> tagRepository.findBySlug(slug)
                .orElseGet(() -> tagRepository.save(
                    Tag.builder()
                        .name(slugToName(slug))
                        .slug(slug)
                        .build())))
            .collect(Collectors.toSet());
    }

    private String slugToName(String slug) {
        // "java-spring-boot" → "Java Spring Boot"
        return java.util.Arrays.stream(slug.split("-"))
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    }

    /** Rough estimate: 200 words per minute reading speed. */
    private int estimateReadTime(String content) {
        int wordCount = content.trim().split("\\s+").length;
        return Math.max(1, (int) Math.ceil(wordCount / 200.0));
    }

    public BlogPostResponse toResponse(BlogPost post) {
        return BlogPostResponse.builder()
            .id(post.getId())
            .title(post.getTitle())
            .slug(post.getSlug())
            .summary(post.getSummary())
            .content(post.getContent())
            .authorId(post.getAuthorId())
            .authorName(post.getAuthorName())
            .categoryName(post.getCategory() != null ? post.getCategory().getName() : null)
            .categorySlug(post.getCategory() != null ? post.getCategory().getSlug() : null)
            .tags(post.getTags().stream().map(Tag::getSlug).collect(Collectors.toSet()))
            .status(post.getStatus())
            .coverImageUrl(post.getCoverImageUrl())
            .viewCount(post.getViewCount())
            .readTimeMinutes(post.getReadTimeMinutes())
            .featured(post.isFeatured())
            .publishedAt(post.getPublishedAt())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt())
            .build();
    }
}
