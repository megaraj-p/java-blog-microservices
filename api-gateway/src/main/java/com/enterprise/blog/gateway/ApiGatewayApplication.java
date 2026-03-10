package com.enterprise.blog.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway — single entry point for all client traffic.
 *
 * Responsibilities:
 * - JWT authentication validation (before forwarding)
 * - Per-user and per-IP rate limiting (via Redis token bucket)
 * - Dynamic routing to microservices via Eureka
 * - Request/response logging with trace ID propagation
 * - CORS policy enforcement
 * - Circuit breaking for upstream service failures
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
