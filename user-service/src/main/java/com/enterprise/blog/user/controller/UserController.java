package com.enterprise.blog.user.controller;

import com.enterprise.blog.user.dto.UpdateProfileRequest;
import com.enterprise.blog.user.dto.UserDto;
import com.enterprise.blog.user.entity.Role;
import com.enterprise.blog.user.entity.User;
import com.enterprise.blog.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /users/me — Get current user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getUserById(user.getId()));
    }

    /**
     * PUT /users/me — Update current user's profile.
     */
    @PutMapping("/me")
    public ResponseEntity<UserDto> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(user.getId(), request));
    }

    /**
     * GET /users/{id} — Get any user by ID (Admin can view any; others can only view themselves).
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(
            @PathVariable UUID id,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        boolean isAdmin = currentUser.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // Non-admins can only view their own profile
        if (!isAdmin && !currentUser.getId().equals(id)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * GET /users — List all users (Admin only).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserDto>> listUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(pageable));
    }

    /**
     * PUT /users/{id}/roles — Update a user's roles (Admin only).
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateRoles(
            @PathVariable UUID id,
            @RequestBody Set<Role> roles) {
        userService.updateRoles(id, roles);
        return ResponseEntity.noContent().build();
    }
}
