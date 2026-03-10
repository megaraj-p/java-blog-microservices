package com.enterprise.blog.analytics.service;

import com.enterprise.blog.analytics.entity.BlogView;
import com.enterprise.blog.analytics.repository.BlogViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final BlogViewRepository blogViewRepository;

    public void recordView(BlogView view) {
        blogViewRepository.save(view);
    }

    @Cacheable(value = "popular-posts", key = "#limit")
    public List<Map<String, Object>> getPopularPosts(int limit) {
        List<Object[]> results = blogViewRepository.findTopPostsByViews(PageRequest.of(0, limit));
        return results.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("blogPostId", row[0]);
            entry.put("viewCount", row[1]);
            return entry;
        }).toList();
    }

    @Cacheable(value = "trending-posts", key = "#hours + '-' + #limit")
    public List<Map<String, Object>> getTrendingPosts(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> results = blogViewRepository.findTrendingPosts(since, PageRequest.of(0, limit));
        return results.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("blogPostId", row[0]);
            entry.put("viewCount", row[1]);
            return entry;
        }).toList();
    }

    public Map<String, Object> getPostStats(String postId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("blogPostId", postId);
        stats.put("totalViews", blogViewRepository.countByBlogPostId(postId));
        return stats;
    }

    public Map<String, Object> getAuthorStats(String authorId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("authorId", authorId);
        stats.put("totalViews", blogViewRepository.countTotalViewsByAuthor(authorId));
        List<Object[]> topPosts = blogViewRepository.findPostViewsByAuthor(authorId, PageRequest.of(0, 5));
        stats.put("topPosts", topPosts.stream().map(row -> {
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("blogPostId", row[0]);
            post.put("viewCount", row[1]);
            return post;
        }).toList());
        return stats;
    }
}
