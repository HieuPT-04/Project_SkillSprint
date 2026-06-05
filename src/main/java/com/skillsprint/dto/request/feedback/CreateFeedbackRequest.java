package com.skillsprint.dto.request.feedback;

import com.skillsprint.enums.feedback.FeedbackType;
import jakarta.validation.constraints.NotBlank;
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
public class CreateFeedbackRequest {

    @NotNull
    FeedbackType type;

    @NotBlank
    @Size(max = 255)
    String title;

    @NotBlank
    @Size(max = 5000)
    String content;

    @Size(max = 1000)
    String relatedUrl;
}
