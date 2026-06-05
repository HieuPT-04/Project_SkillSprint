package com.skillsprint.dto.request.feedback;

import com.skillsprint.enums.feedback.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateFeedbackStatusRequest {

    @NotNull
    FeedbackStatus status;

    @Size(max = 5000)
    String adminNote;
}
