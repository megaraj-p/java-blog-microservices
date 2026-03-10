package com.enterprise.blog.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request logging filter.
 *
 * Logs incoming request path, method, and upstream response status with
 * elapsed time for every request. Injects a unique request-id for
 * correlation with distributed traces.
 *
 * NOTE: For production rate limiting use the Spring Cloud Gateway
 * RequestRateLimiter filter backed by Redis (configured in application.yml).
 * This filter adds per-request structured logging only.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getId();
        long startTime = System.currentTimeMillis();

        String clientIp = getClientIp(request);
        String method   = request.getMethod().name();
        String path     = request.getURI().getPath();

        log.info("→ {} {} | requestId={} | ip={}", method, path, requestId, clientIp);

        // Inject request-id for trace correlation
        ServerHttpRequest mutated = request.mutate()
            .header("X-Request-Id", requestId)
            .build();

        return chain.filter(exchange.mutate().request(mutated).build())
            .doFinally(signal -> {
                long elapsed = System.currentTimeMillis() - startTime;
                int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
                log.info("← {} {} | status={} | {}ms | requestId={}", method, path, status, elapsed, requestId);
            });
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can contain a chain — take the first (original client IP)
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -200; // Run before auth filter
    }
}
