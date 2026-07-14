package com.skillsprint.dto.request.marketplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitMarketplaceQuizRequest {

    @NotEmpty(message = "Cần gửi đủ đáp án của Quiz Pack")
    @Valid
    private List<AnswerRequest> answers;

    @NotNull(message = "durationSeconds không được để trống")
    @Min(value = 0, message = "durationSeconds không được âm")
    private Long durationSeconds;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AnswerRequest {

        @NotNull(message = "questionId không được để trống")
        private UUID questionId;

        @NotNull(message = "selectedOptionId không được để trống")
        private UUID selectedOptionId;
    }
}
