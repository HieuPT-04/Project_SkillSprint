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
public class FeedbackSubmitResponse {

    UUID feedbackId;
    FeedbackType type;
    String title;
    FeedbackStatus status;
    Instant createdAt;
}
