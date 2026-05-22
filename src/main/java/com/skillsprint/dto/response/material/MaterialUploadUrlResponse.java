package com.skillsprint.dto.response.material;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaterialUploadUrlResponse {

    String uploadUrl;
    String fileUrl;
    String objectKey;
    Instant expiresAt;
}
