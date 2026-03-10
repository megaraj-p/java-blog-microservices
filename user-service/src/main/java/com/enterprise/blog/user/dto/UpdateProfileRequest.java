package com.enterprise.blog.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class UpdateProfileRequest {

    @Size(max = 100)
    private String displayName;

    @Size(max = 1000)
    private String bio;

    @URL(message = "Avatar URL must be a valid URL")
    @Size(max = 500)
    private String avatarUrl;
}
