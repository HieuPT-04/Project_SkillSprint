package com.skillsprint.mapper;

import com.skillsprint.configuration.s3.S3Properties;
import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.entity.User;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserMapper {

    S3Properties s3Properties;

    public MeResponse toMeResponse(User user, List<String> roles) {
        return MeResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .fullName(user.getFullName())
                .avatarUrl(buildFileUrl(user.getAvatarObjectKey()))
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
                .avatarUrl(buildFileUrl(user.getAvatarObjectKey()))
                .timeZone(user.getTimeZone())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String buildFileUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return s3Properties.publicBaseUrl().replaceAll("/+$", "") + "/" + objectKey;
    }
}
