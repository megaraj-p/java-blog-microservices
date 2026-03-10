package com.enterprise.blog.analytics.repository;

import com.enterprise.blog.analytics.entity.BlogView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BlogViewRepository extends JpaRepository<BlogView, UUID> {

    long countByBlogPostId(String blogPostId);

    @Query("SELECT v.blogPostId, COUNT(v) as viewCount FROM BlogView v " +
           "GROUP BY v.blogPostId ORDER BY viewCount DESC")
    List<Object[]> findTopPostsByViews(Pageable pageable);

    @Query("SELECT v.blogPostId, COUNT(v) as viewCount FROM BlogView v " +
           "WHERE v.viewedAt >= :since GROUP BY v.blogPostId ORDER BY viewCount DESC")
    List<Object[]> findTrendingPosts(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT COUNT(v) FROM BlogView v WHERE v.authorId = :authorId")
    long countTotalViewsByAuthor(@Param("authorId") String authorId);

    @Query("SELECT v.blogPostId, COUNT(v) as views FROM BlogView v " +
           "WHERE v.authorId = :authorId GROUP BY v.blogPostId ORDER BY views DESC")
    List<Object[]> findPostViewsByAuthor(@Param("authorId") String authorId, Pageable pageable);
}
