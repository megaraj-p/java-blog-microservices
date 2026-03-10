package com.enterprise.blog.user.mapper;

import com.enterprise.blog.user.dto.UserDto;
import com.enterprise.blog.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    @Mapping(target = "id",          source = "id")
    @Mapping(target = "username",    source = "username")
    @Mapping(target = "email",       source = "email")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "bio",         source = "bio")
    @Mapping(target = "avatarUrl",   source = "avatarUrl")
    @Mapping(target = "roles",       source = "roles")
    @Mapping(target = "enabled",     source = "enabled")
    @Mapping(target = "createdAt",   source = "createdAt")
    UserDto toDto(User user);
}
