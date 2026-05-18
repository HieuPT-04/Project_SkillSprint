package com.skillsprint.dto.response.user;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvatarUploadUrlResponse {

    String uploadUrl;
    String fileUrl;
    String objectKey;
    Instant expiresAt;
}
