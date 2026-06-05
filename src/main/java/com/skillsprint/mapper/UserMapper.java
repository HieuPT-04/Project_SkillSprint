package com.skillsprint.mapper;

import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.entity.User;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserMapper {

    S3PresignedUrlService s3PresignedUrlService;

    public MeResponse toMeResponse(User user, List<String> roles) {
        return MeResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .fullName(user.getFullName())
                .avatarUrl(s3PresignedUrlService.createViewUrl(user.getAvatarObjectKey()))
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
                .avatarUrl(s3PresignedUrlService.createViewUrl(user.getAvatarObjectKey()))
                .timeZone(user.getTimeZone())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
