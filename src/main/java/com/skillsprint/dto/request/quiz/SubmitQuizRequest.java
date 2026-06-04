package com.skillsprint.dto.request.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmitQuizRequest {

    @Valid
    @NotEmpty(message = "Cần gửi ít nhất một đáp án")
    List<AnswerRequest> answers;

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AnswerRequest {

        @NotNull(message = "questionId không được để trống")
        UUID questionId;

        @NotNull(message = "selectedOptionId không được để trống")
        UUID selectedOptionId;
    }
}
