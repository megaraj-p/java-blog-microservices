package com.enterprise.blog.user.service;

import com.enterprise.blog.user.dto.RegisterRequest;
import com.enterprise.blog.user.dto.UpdateProfileRequest;
import com.enterprise.blog.user.dto.UserDto;
import com.enterprise.blog.user.entity.Role;
import com.enterprise.blog.user.entity.User;
import com.enterprise.blog.user.event.UserEventProducer;
import com.enterprise.blog.user.exception.ConflictException;
import com.enterprise.blog.user.exception.ResourceNotFoundException;
import com.enterprise.blog.user.mapper.UserMapper;
import com.enterprise.blog.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Core user management service.
 *
 * Also implements UserDetailsService so Spring Security can load
 * users during the authentication filter chain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserEventProducer userEventProducer;

    // ── UserDetailsService ──────────────────────────────

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    // ── Registration ────────────────────────────────────

    @Transactional
    public User registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already taken: " + request.getUsername());
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername())
            .roles(Set.of(Role.READER))
            .enabled(true)
            .accountNonLocked(true)
            .build();

        User saved = userRepository.save(user);
        log.info("New user registered: id={}, email={}", saved.getId(), saved.getEmail());

        // Publish async event — notification service will send welcome email
        userEventProducer.publishUserRegistered(saved);

        return saved;
    }

    // ── Read ────────────────────────────────────────────

    @Cacheable(value = "users", key = "#userId")
    public UserDto getUserById(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return userMapper.toDto(user);
    }

    public Page<UserDto> listUsers(Pageable pageable) {
        return userRepository.findByEnabledTrue(pageable)
            .map(userMapper::toDto);
    }

    // ── Update ──────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User saved = userRepository.save(user);
        log.info("User profile updated: id={}", saved.getId());
        return userMapper.toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void updateRoles(UUID userId, Set<Role> newRoles) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setRoles(newRoles);
        userRepository.save(user);
        log.info("Roles updated for userId={}: {}", userId, newRoles);
    }

    // ── Account Lock / Login Tracking ───────────────────

    @Transactional
    public void handleFailedLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            userRepository.incrementFailedLoginAttempts(user.getId());
            if (user.getFailedLoginAttempts() + 1 >= 5) {
                userRepository.lockAccount(user.getId());
                log.warn("Account locked after 5 failed attempts: userId={}", user.getId());
            }
        });
    }

    @Transactional
    public void handleSuccessfulLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user ->
            userRepository.resetFailedLoginAttempts(user.getId())
        );
    }
}
