package com.enterprise.blog.user.controller;

import com.enterprise.blog.user.dto.AuthResponse;
import com.enterprise.blog.user.dto.LoginRequest;
import com.enterprise.blog.user.dto.RegisterRequest;
import com.enterprise.blog.user.entity.User;
import com.enterprise.blog.user.service.JwtService;
import com.enterprise.blog.user.service.RefreshTokenService;
import com.enterprise.blog.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller.
 *
 * All endpoints here are public (no JWT required).
 * Returns access token + refresh token on successful login.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.access-token.expiration-ms:900000}")
    private long accessTokenExpirationMs;

    /**
     * POST /auth/register
     * Register a new user account.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);
        log.info("User registered successfully: {}", user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "message", "Registration successful. Welcome, " + user.getDisplayName() + "!",
                "userId", user.getId().toString()
            ));
    }

    /**
     * POST /auth/login
     * Authenticate with email and password.
     * Returns access token (short-lived) + refresh token (long-lived).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException ex) {
            userService.handleFailedLogin(request.getEmail());
            throw ex; // GlobalExceptionHandler maps to 401
        }

        User user = (User) auth.getPrincipal();
        userService.handleSuccessfulLogin(user.getEmail());

        String accessToken = jwtService.generateAccessToken(user, user.getId().toString());
        String deviceInfo  = httpRequest.getHeader("User-Agent");
        String rawRefresh  = refreshTokenService.createRefreshToken(user, deviceInfo);

        AuthResponse response = AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(rawRefresh)
            .tokenType("Bearer")
            .expiresIn(accessTokenExpirationMs / 1000)
            .userId(user.getId().toString())
            .email(user.getEmail())
            .roles(user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .toList())
            .build();

        log.info("User logged in: userId={}", user.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /auth/refresh
     * Exchange a refresh token for a new access token.
     * Implements token rotation — the old refresh token is invalidated.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String rawRefreshToken = body.get("refreshToken");
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        User user = refreshTokenService.validateAndRotate(rawRefreshToken);
        String newAccessToken  = jwtService.generateAccessToken(user, user.getId().toString());
        String newRefreshToken = refreshTokenService.createRefreshToken(user, "token-refresh");

        AuthResponse response = AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .tokenType("Bearer")
            .expiresIn(accessTokenExpirationMs / 1000)
            .userId(user.getId().toString())
            .email(user.getEmail())
            .roles(user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .toList())
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /auth/logout
     * Revoke all refresh tokens for the current user (logout from all devices).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (authentication != null) {
            User user = (User) authentication.getPrincipal();
            refreshTokenService.revokeAllUserTokens(user.getId());
            log.info("User logged out: userId={}", user.getId());
        }
        return ResponseEntity.noContent().build();
    }
}
