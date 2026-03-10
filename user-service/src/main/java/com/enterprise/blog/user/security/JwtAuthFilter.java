package com.enterprise.blog.user.security;

import com.enterprise.blog.user.service.JwtService;
import com.enterprise.blog.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter for the User Service.
 *
 * The API Gateway validates JWTs and injects X-User-Id / X-User-Roles headers.
 * When called directly (e.g. service-to-service or health checks), this filter
 * also validates the raw JWT from the Authorization header as a fallback.
 *
 * This is a per-request filter (OncePerRequestFilter).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Check if gateway already processed and injected user context
        String userId = request.getHeader("X-User-Id");
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromHeader(request, userId);
            filterChain.doFilter(request, response);
            return;
        }

        // Fallback: validate raw JWT (direct service access)
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            String subjectId = jwtService.extractSubject(token);
            if (subjectId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load user details from DB — necessary for UserDetails authorities
                // Note: subject is the user UUID, but loadUserByUsername expects email
                // In production use a dedicated loadById method
                if (jwtService.isTokenValid(token, subjectId)) {
                    String email = jwtService.extractClaim(token, claims -> claims.get("email", String.class));
                    UserDetails userDetails = userService.loadUserByUsername(email);

                    var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not authenticate from JWT: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateFromHeader(HttpServletRequest request, String userId) {
        try {
            String email = request.getHeader("X-User-Email");
            if (email != null) {
                UserDetails userDetails = userService.loadUserByUsername(email);
                var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception ex) {
            log.warn("Failed to set security context from gateway headers: {}", ex.getMessage());
        }
    }
}
