package com.skillsprint.dto.response.feedback;

import com.skillsprint.enums.feedback.FeedbackStatus;
import com.skillsprint.enums.feedback.FeedbackType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedbackResponse {

    UUID feedbackId;
    String userId;
    String userEmail;
    String userFullName;
    FeedbackType type;
    String title;
    String content;
    String relatedUrl;
    FeedbackStatus status;
    String adminNote;
    Instant createdAt;
    Instant updatedAt;
}
