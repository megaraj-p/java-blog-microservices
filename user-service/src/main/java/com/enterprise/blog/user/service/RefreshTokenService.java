package com.enterprise.blog.user.service;

import com.enterprise.blog.user.entity.RefreshToken;
import com.enterprise.blog.user.entity.User;
import com.enterprise.blog.user.exception.InvalidTokenException;
import com.enterprise.blog.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages opaque refresh tokens.
 *
 * Each refresh token is a cryptographically random 256-bit value.
 * Only the SHA-256 hash is persisted, so even if the DB is compromised
 * the raw tokens cannot be recovered.
 *
 * A scheduled job purges expired tokens nightly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token.expiration-ms:604800000}")
    private long refreshTokenExpirationMs; // Default 7 days

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a new refresh token for the user, persists its hash, and returns the raw value.
     * The raw value is returned ONCE and must be stored securely by the client
     * (e.g. in an HttpOnly cookie).
     */
    @Transactional
    public String createRefreshToken(User user, String deviceInfo) {
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(tokenHash)
            .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
            .deviceInfo(deviceInfo)
            .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token created for userId={}", user.getId());
        return rawToken;
    }

    /**
     * Validates and marks the token as used (rotates it).
     * Returns the associated user if valid.
     */
    @Transactional
    public User validateAndRotate(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!stored.isValid()) {
            // Token already expired or revoked — revoke all tokens for this user (potential reuse attack)
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            throw new InvalidTokenException("Refresh token is expired or revoked. All sessions have been invalidated.");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return stored.getUser();
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for userId={}", userId);
    }

    /** Nightly cleanup of expired tokens to keep the table lean. */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM every day
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpired();
        log.info("Purged {} expired refresh tokens", deleted);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
