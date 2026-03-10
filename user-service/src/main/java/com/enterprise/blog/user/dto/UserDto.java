package com.enterprise.blog.user.dto;

import com.enterprise.blog.user.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserDto {
    private UUID id;
    private String username;
    private String email;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private Set<Role> roles;
    private boolean enabled;
    private Instant createdAt;
}
