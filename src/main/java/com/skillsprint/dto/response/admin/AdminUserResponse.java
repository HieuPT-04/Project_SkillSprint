package com.skillsprint.dto.response.admin;

import com.skillsprint.enums.auth.UserStatus;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserResponse {

    String userId;
    String email;
    boolean emailVerified;
    String fullName;
    String avatarUrl;
    String timeZone;
    UserStatus status;
    List<String> roles;
    Instant lastLoginAt;
    Instant createdAt;
    Instant updatedAt;
}
