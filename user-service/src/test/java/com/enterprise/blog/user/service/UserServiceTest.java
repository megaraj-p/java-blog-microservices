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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 * Tests cover all critical business logic paths and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserEventProducer userEventProducer;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID testUserId;
    private String testEmail;
    private String testUsername;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
        testUsername = "testuser";

        testUser = User.builder()
                .id(testUserId)
                .username(testUsername)
                .email(testEmail)
                .passwordHash("hashedPassword")
                .displayName("Test User")
                .roles(Set.of(Role.READER))
                .enabled(true)
                .accountNonLocked(true)
                .failedLoginAttempts(0)
                .build();
    }

    // ══════════════════════════════════════════════════
    // Load User by Username (UserDetailsService)
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("loadUserByUsername: Should return user when found")
    void loadUserByUsername_Success() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        UserDetails result = userService.loadUserByUsername(testEmail);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(testUsername);
        assertThat(((User) result).getEmail()).isEqualTo(testEmail);
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    @DisplayName("loadUserByUsername: Should throw exception when user not found")
    void loadUserByUsername_NotFound() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.loadUserByUsername(testEmail))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found with email: " + testEmail);
        verify(userRepository).findByEmail(testEmail);
    }

    // ══════════════════════════════════════════════════
    // Register User
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("registerUser: Should successfully register new user")
    void registerUser_Success() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setDisplayName("New User");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        User result = userService.registerUser(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testUser);
        verify(userRepository).existsByEmail(request.getEmail());
        verify(userRepository).existsByUsername(request.getUsername());
        verify(passwordEncoder).encode(request.getPassword());
        verify(userRepository).save(any(User.class));
        verify(userEventProducer).publishUserRegistered(testUser);
    }

    @Test
    @DisplayName("registerUser: Should throw ConflictException when email exists")
    void registerUser_EmailAlreadyExists() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail(testEmail);
        request.setUsername("newuser");
        request.setPassword("password123");

        when(userRepository.existsByEmail(testEmail)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use: " + testEmail);
        verify(userRepository).existsByEmail(testEmail);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerUser: Should throw ConflictException when username exists")
    void registerUser_UsernameAlreadyTaken() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setUsername(testUsername);
        request.setPassword("password123");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(testUsername)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken: " + testUsername);
        verify(userRepository).existsByEmail(request.getEmail());
        verify(userRepository).existsByUsername(testUsername);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerUser: Should use username as displayName when not provided")
    void registerUser_NoDisplayName() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setUsername("newuser");
        request.setPassword("password123");
        // displayName is null

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        userService.registerUser(request);

        // Assert
        verify(userRepository).save(argThat(user -> user.getDisplayName().equals(request.getUsername())));
    }

    // ══════════════════════════════════════════════════
    // Get User by ID
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("getUserById: Should return user DTO when found")
    void getUserById_Success() {
        // Arrange
        UserDto expectedDto = UserDto.builder()
                .id(testUserId)
                .email(testEmail)
                .username(testUsername)
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userMapper.toDto(testUser)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.getUserById(testUserId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUserId);
        assertThat(result.getEmail()).isEqualTo(testEmail);
        verify(userRepository).findById(testUserId);
        verify(userMapper).toDto(testUser);
    }

    @Test
    @DisplayName("getUserById: Should throw ResourceNotFoundException when not found")
    void getUserById_NotFound() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found: " + testUserId);
        verify(userRepository).findById(testUserId);
        verify(userMapper, never()).toDto(any());
    }

    // ══════════════════════════════════════════════════
    // List Users
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("listUsers: Should return paginated list of enabled users")
    void listUsers_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(testUser));
        UserDto userDto = UserDto.builder()
                .id(testUserId)
                .username(testUsername)
                .email(testEmail)
                .build();

        when(userRepository.findByEnabledTrue(pageable)).thenReturn(userPage);
        when(userMapper.toDto(testUser)).thenReturn(userDto);

        // Act
        Page<UserDto> result = userService.listUsers(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(testUserId);
        verify(userRepository).findByEnabledTrue(pageable);
    }

    // ══════════════════════════════════════════════════
    // Update Profile
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("updateProfile: Should update all provided fields")
    void updateProfile_Success() {
        // Arrange
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Updated Name");
        request.setBio("Updated bio");
        request.setAvatarUrl("https://example.com/avatar.jpg");

        UserDto expectedDto = UserDto.builder()
                .id(testUserId)
                .username(testUsername)
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toDto(testUser)).thenReturn(expectedDto);

        // Act
        UserDto result = userService.updateProfile(testUserId, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testUserId);
        assertThat(testUser.getDisplayName()).isEqualTo("Updated Name");
        assertThat(testUser.getBio()).isEqualTo("Updated bio");
        assertThat(testUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateProfile: Should only update non-null fields")
    void updateProfile_PartialUpdate() {
        // Arrange
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Updated Name");
        // bio and avatarUrl are null

        UserDto expectedDto = UserDto.builder()
                .id(testUserId)
                .username(testUsername)
                .build();
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toDto(testUser)).thenReturn(expectedDto);

        // Act
        userService.updateProfile(testUserId, request);

        // Assert
        assertThat(testUser.getDisplayName()).isEqualTo("Updated Name");
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateProfile: Should throw ResourceNotFoundException when user not found")
    void updateProfile_UserNotFound() {
        // Arrange
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("New Name");

        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateProfile(testUserId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found: " + testUserId);
        verify(userRepository).findById(testUserId);
        verify(userRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════
    // Update Roles
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("updateRoles: Should successfully update user roles")
    void updateRoles_Success() {
        // Arrange
        Set<Role> newRoles = Set.of(Role.READER, Role.AUTHOR);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);

        // Act
        userService.updateRoles(testUserId, newRoles);

        // Assert
        assertThat(testUser.getRoles()).isEqualTo(newRoles);
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateRoles: Should throw ResourceNotFoundException when user not found")
    void updateRoles_UserNotFound() {
        // Arrange
        Set<Role> newRoles = Set.of(Role.AUTHOR);
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateRoles(testUserId, newRoles))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found: " + testUserId);
        verify(userRepository).findById(testUserId);
        verify(userRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════
    // Handle Failed Login
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("handleFailedLogin: Should increment failed attempts counter")
    void handleFailedLogin_IncrementCounter() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        userService.handleFailedLogin(testEmail);

        // Assert
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository).incrementFailedLoginAttempts(testUserId);
    }

    @Test
    @DisplayName("handleFailedLogin: Should lock account after 5 failed attempts")
    void handleFailedLogin_LockAccount() {
        // Arrange
        testUser.setFailedLoginAttempts(4); // Next attempt will be 5
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        userService.handleFailedLogin(testEmail);

        // Assert
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository).incrementFailedLoginAttempts(testUserId);
        verify(userRepository).lockAccount(testUserId);
    }

    @Test
    @DisplayName("handleFailedLogin: Should handle non-existent user gracefully")
    void handleFailedLogin_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act
        userService.handleFailedLogin(testEmail);

        // Assert
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).incrementFailedLoginAttempts(any());
    }

    // ══════════════════════════════════════════════════
    // Handle Successful Login
    // ══════════════════════════════════════════════════

    @Test
    @DisplayName("handleSuccessfulLogin: Should reset failed attempts counter")
    void handleSuccessfulLogin_Success() {
        // Arrange
        testUser.setFailedLoginAttempts(3);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Act
        userService.handleSuccessfulLogin(testEmail);

        // Assert
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository).resetFailedLoginAttempts(testUserId);
    }

    @Test
    @DisplayName("handleSuccessfulLogin: Should handle non-existent user gracefully")
    void handleSuccessfulLogin_UserNotFound() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act
        userService.handleSuccessfulLogin(testEmail);

        // Assert
        verify(userRepository).findByEmail(testEmail);
        verify(userRepository, never()).resetFailedLoginAttempts(any());
    }
}
