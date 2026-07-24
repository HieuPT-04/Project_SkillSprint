package com.skillsprint.mapper;

import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.entity.User;
import com.skillsprint.entity.Subscription;
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

    // Hàm gốc đã được nâng cấp nhận thêm tham số thực thể Subscription
    public AdminUserResponse toAdminUserResponse(User user, List<String> roles, Subscription subscription) {
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
                .currentSubscription(toSubscriptionAdminResponse(subscription)) // Gán thông tin gói dịch vụ đăng ký ở đây
                .build();
    }

    // Hàm bổ trợ phụ để map Object con Subscription từ Entity sang DTO
    private AdminUserResponse.SubscriptionAdminResponse toSubscriptionAdminResponse(Subscription subscription) {
        if (subscription == null) {
            return null;
        }

        return AdminUserResponse.SubscriptionAdminResponse.builder()
                .subscriptionId(subscription.getSubscriptionId() != null ? subscription.getSubscriptionId().toString() : null)
                .planName(subscription.getPlan() != null ? subscription.getPlan().getPlanName() : null)
                .planType(subscription.getPlan() != null && subscription.getPlan().getPlanType() != null
                        ? subscription.getPlan().getPlanType().name()
                        : null)
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus() != null ? subscription.getStatus().name() : null)
                .build();
    }
}
