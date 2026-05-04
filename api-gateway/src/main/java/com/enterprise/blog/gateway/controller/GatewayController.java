package com.enterprise.blog.gateway.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class GatewayController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "service", "api-gateway",
                "status", "running",
                "routes", Map.of(
                        "auth", "/auth/**",
                        "blogs", "/blogs/**",
                        "comments", "/comments/**",
                        "search", "/search/**",
                        "analytics", "/analytics/**")));
    }

    @GetMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "service", "api-gateway",
                "status", "fallback",
                "message", "A routed downstream service is temporarily unavailable."));
    }
}