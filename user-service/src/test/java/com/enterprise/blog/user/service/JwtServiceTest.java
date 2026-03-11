package com.enterprise.blog.user.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtService}.
 * Tests cover token generation, validation, and claim extraction.
 */
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-for-jwt-must-be-at-least-256-bits-long-to-work-properly";
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 900000L; // 15 minutes
    private static final String TEST_USER_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // Inject test values using ReflectionTestUtils
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", ACCESS_TOKEN_EXPIRATION_MS);

        // Call @PostConstruct manually
        jwtService.init();
    }

    // ══════════════════════════════════════════════════
    // Token Generation
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("generateAccessToken: Should generate valid token with user details")
    void generateAccessToken_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER", "ROLE_AUTHOR");

        // Act
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Assert
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts: header.payload.signature
    }

    @Test
    @DisplayName("generateAccessToken: Should include user email in claims")
    void generateAccessToken_IncludesEmail() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");

        // Act
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Assert
        Claims claims = extractAllClaimsUsingService(token);
        assertThat(claims.get("email", String.class)).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("generateAccessToken: Should include roles without ROLE_ prefix")
    void generateAccessToken_IncludesRolesWithoutPrefix() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER", "ROLE_AUTHOR");

        // Act
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Assert
        List<String> roles = jwtService.extractRoles(token);
        assertThat(roles).containsExactlyInAnyOrder("READER", "AUTHOR");
        assertThat(roles).doesNotContain("ROLE_READER", "ROLE_AUTHOR");
    }

    @Test
    @DisplayName("generateAccessToken: Should set subject to userId")
    void generateAccessToken_SetsSubjectToUserId() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");

        // Act
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Assert
        String subject = jwtService.extractSubject(token);
        assertThat(subject).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("generateAccessToken: Should set expiration time correctly")
    void generateAccessToken_SetsExpiration() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        long beforeGeneration = System.currentTimeMillis();

        // Act
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Assert
        Date expiration = jwtService.extractExpiration(token);
        long expirationTime = expiration.getTime();
        long expectedExpiration = beforeGeneration + ACCESS_TOKEN_EXPIRATION_MS;

        // Allow 1 second buffer for timing variations
        assertThat(expirationTime)
                .isGreaterThan(beforeGeneration)
                .isCloseTo(expectedExpiration, within(1000L));
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        boolean isValid = jwtService.isTokenValid(token, TEST_USER_ID);

        // Assert
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: Should return false when userId mismatch")
    void isTokenValid_UserIdMismatch() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);
        String differentUserId = "different-user-id";

        // Act
        boolean isValid = jwtService.isTokenValid(token, differentUserId);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: Should return false for expired token")
    void isTokenValid_ExpiredToken() {
        // Arrange
        JwtService shortLivedJwtService = new JwtService();
        ReflectionTestUtils.setField(shortLivedJwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedJwtService, "accessTokenExpirationMs", -1000L); // Already expired
        shortLivedJwtService.init();

        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = shortLivedJwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        boolean isValid = shortLivedJwtService.isTokenValid(token, TEST_USER_ID);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: Should return false for malformed token")
    void isTokenValid_MalformedToken() {
        // Arrange
        String malformedToken = "not.a.valid.jwt.token";

        // Act
        boolean isValid = jwtService.isTokenValid(malformedToken, TEST_USER_ID);

        // Assert
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid: Should return false for empty token")
    void isTokenValid_EmptyToken() {
        // Arrange
        String emptyToken = "";

        // Act
        boolean isValid = jwtService.isTokenValid(emptyToken, TEST_USER_ID);

        // Assert
        assertThat(isValid).isFalse();
    }

    // ══════════════════════════════════════════════════
    // Claim Extraction
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("extractSubject: Should extract userId from token")
    void extractSubject_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        String subject = jwtService.extractSubject(token);

        // Assert
        assertThat(subject).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("extractExpiration: Should extract expiration date from token")
    void extractExpiration_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        Date expiration = jwtService.extractExpiration(token);

        // Assert
        assertThat(expiration).isNotNull();
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    @DisplayName("extractRoles: Should extract roles list from token")
    void extractRoles_Success() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_ADMIN", "ROLE_MODERATOR");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        List<String> roles = jwtService.extractRoles(token);

        // Assert
        assertThat(roles).isNotNull();
        assertThat(roles).containsExactlyInAnyOrder("ADMIN", "MODERATOR");
    }

    @Test
    @DisplayName("extractRoles: Should return empty list when no roles")
    void extractRoles_NoRoles() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL); // No roles
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        List<String> roles = jwtService.extractRoles(token);

        // Assert
        assertThat(roles).isNotNull();
        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("extractClaim: Should extract custom claim using function")
    void extractClaim_CustomClaimExtraction() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act
        String email = jwtService.extractClaim(token, claims -> claims.get("email", String.class));

        // Assert
        assertThat(email).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("extractClaim: Should throw exception for invalid token")
    void extractClaim_InvalidToken() {
        // Arrange
        String invalidToken = "invalid.token.here";

        // Act & Assert
        assertThatThrownBy(() -> jwtService.extractSubject(invalidToken))
                .isInstanceOf(JwtException.class);
    }

    // ══════════════════════════════════════════════════
    // Token Security
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("Token generated with one secret should not validate with different secret")
    void tokenSecurity_DifferentSecret() {
        // Arrange
        JwtService anotherJwtService = new JwtService();
        String differentSecret = "different-secret-key-for-jwt-must-be-at-least-256-bits-long-to-work";
        ReflectionTestUtils.setField(anotherJwtService, "jwtSecret", differentSecret);
        ReflectionTestUtils.setField(anotherJwtService, "accessTokenExpirationMs", ACCESS_TOKEN_EXPIRATION_MS);
        anotherJwtService.init();

        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Act & Assert
        assertThatThrownBy(() -> anotherJwtService.extractSubject(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token tampering should be detected")
    void tokenSecurity_TamperedToken() {
        // Arrange
        UserDetails userDetails = createUserDetails(TEST_EMAIL, "ROLE_READER");
        String token = jwtService.generateAccessToken(userDetails, TEST_USER_ID);

        // Tamper with token by modifying a character in payload section
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + ".eyJhbGciOiJIUzI1NiJ9." + parts[2];

        // Act & Assert
        assertThatThrownBy(() -> jwtService.extractSubject(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    // ══════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════

    private UserDetails createUserDetails(String email, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        return User.builder()
                .username(email)
                .password("password")
                .authorities(authorities)
                .build();
    }

    /**
     * Helper to extract all claims for assertion purposes.
     * Uses reflection to access private method for testing.
     */
    private Claims extractAllClaimsUsingService(String token) {
        try {
            return jwtService.extractClaim(token, claims -> claims);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract claims", e);
        }
    }
}
