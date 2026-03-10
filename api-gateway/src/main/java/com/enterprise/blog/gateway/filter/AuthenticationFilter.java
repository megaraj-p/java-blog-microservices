package com.enterprise.blog.gateway.filter;

import com.enterprise.blog.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global JWT Authentication Filter.
 *
 * Extracts and validates the JWT from the Authorization header before any
 * request reaches an upstream microservice. On success, injects
 * X-User-Id, X-User-Email, and X-User-Roles headers so downstream services
 * can trust the authenticated identity without re-validating the token.
 *
 * Public paths (login, register, public blog reads, health checks) are
 * explicitly bypassed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    /** Paths that do NOT require a valid JWT. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/actuator",
            "/blogs", // Public read — listing/viewing published posts
            "/search", // Search is public
            "/categories",
            "/tags");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Allow public paths through without a token
        if (isPublicPath(path, request.getMethod().name())) {
            return chain.filter(exchange);
        }

        // Require Authorization header
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            log.warn("Missing Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            jwtUtil.validateToken(token);

            String userId = jwtUtil.extractUserId(token);
            String userEmail = jwtUtil.extractEmail(token);
            String userRoles = String.join(",", jwtUtil.extractRoles(token));

            // Inject context headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", userEmail)
                    .header("X-User-Roles", userRoles)
                    .build();

            log.debug("Authenticated request — userId={}, path={}", userId, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception ex) {
            log.warn("JWT validation failed for path={}: {}", path, ex.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * Public path logic:
     * - Any path starting with a known public prefix is open for GET requests.
     * - Write operations (POST/PUT/DELETE) on /blogs still require auth.
     */
    private boolean isPublicPath(String path, String method) {
        // Auth endpoints always public
        if (path.startsWith("/auth/")) {
            return true;
        }
        // Health/metrics always public
        if (path.startsWith("/actuator")) {
            return true;
        }
        // Search always public
        if (path.startsWith("/search")) {
            return true;
        }
        // Blog and category listing/reading is public for GET only
        if ("GET".equalsIgnoreCase(method)) {
            return path.startsWith("/blogs")
                    || path.startsWith("/categories")
                    || path.startsWith("/tags");
        }
        return false;
    }

    /** Run before routing filter (order 0). */
    @Override
    public int getOrder() {
        return -100;
    }
}
