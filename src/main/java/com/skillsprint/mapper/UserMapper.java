package com.skillsprint.mapper;

import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public MeResponse toMeResponse(User user, List<String> roles) {
        return MeResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .timeZone(user.getTimeZone())
                .status(user.getStatus())
                .roles(roles)
                .build();
    }

    public AdminUserResponse toAdminUserResponse(User user, List<String> roles) {
        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .timeZone(user.getTimeZone())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
