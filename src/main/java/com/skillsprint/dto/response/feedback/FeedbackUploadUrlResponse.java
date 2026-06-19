package com.skillsprint.dto.response.feedback;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedbackUploadUrlResponse {

    String uploadUrl;
    String fileUrl;
    String objectKey;
    Instant expiresAt;
}
