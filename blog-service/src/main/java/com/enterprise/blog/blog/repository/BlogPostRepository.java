package com.enterprise.blog.blog.repository;

import com.enterprise.blog.blog.entity.BlogPost;
import com.enterprise.blog.blog.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    Page<BlogPost> findByStatusOrderByPublishedAtDesc(PostStatus status, Pageable pageable);

    Page<BlogPost> findByAuthorIdOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

    Optional<BlogPost> findBySlugAndStatus(String slug, PostStatus status);

    Optional<BlogPost> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("""
        SELECT bp FROM BlogPost bp
        JOIN bp.tags t
        WHERE bp.status = :status AND t.slug = :tagSlug
        ORDER BY bp.publishedAt DESC
        """)
    Page<BlogPost> findByStatusAndTagSlug(
        @Param("status") PostStatus status,
        @Param("tagSlug") String tagSlug,
        Pageable pageable);

    @Query("""
        SELECT bp FROM BlogPost bp
        WHERE bp.status = :status AND bp.category.slug = :categorySlug
        ORDER BY bp.publishedAt DESC
        """)
    Page<BlogPost> findByStatusAndCategorySlug(
        @Param("status") PostStatus status,
        @Param("categorySlug") String categorySlug,
        Pageable pageable);

    @Modifying
    @Query("UPDATE BlogPost bp SET bp.viewCount = bp.viewCount + 1 WHERE bp.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    @Query("""
        SELECT bp FROM BlogPost bp
        WHERE bp.status = 'PUBLISHED'
        ORDER BY bp.viewCount DESC
        """)
    Page<BlogPost> findTopByViewCount(Pageable pageable);
}
