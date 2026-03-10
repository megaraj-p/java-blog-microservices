package com.enterprise.blog.analytics.controller;

import com.enterprise.blog.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/popular")
    public ResponseEntity<List<Map<String, Object>>> getPopularPosts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getPopularPosts(Math.min(limit, 50)));
    }

    @GetMapping("/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrendingPosts(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTrendingPosts(hours, Math.min(limit, 50)));
    }

    @GetMapping("/posts/{postId}/views")
    public ResponseEntity<Map<String, Object>> getPostStats(@PathVariable String postId) {
        return ResponseEntity.ok(analyticsService.getPostStats(postId));
    }

    @GetMapping("/authors/{authorId}")
    public ResponseEntity<Map<String, Object>> getAuthorStats(@PathVariable String authorId) {
        return ResponseEntity.ok(analyticsService.getAuthorStats(authorId));
    }
}
