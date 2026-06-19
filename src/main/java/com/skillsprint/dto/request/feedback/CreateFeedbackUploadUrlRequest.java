package com.skillsprint.dto.request.feedback;

import jakarta.validation.constraints.NotBlank;
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
public class CreateFeedbackUploadUrlRequest {

    @NotBlank
    @Size(max = 255)
    String fileName;

    @NotBlank
    @Size(max = 100)
    String contentType;
}
