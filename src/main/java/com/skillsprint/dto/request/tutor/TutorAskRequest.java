package com.skillsprint.dto.request.tutor;

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
public class TutorAskRequest {

    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(max = 1000, message = "Câu hỏi tối đa 1000 ký tự")
    String question;
}
