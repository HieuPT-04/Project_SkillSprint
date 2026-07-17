package com.skillsprint.dto.request.marketplace;

import jakarta.validation.Valid;
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
public class SubmitMarketplaceRankedAttemptRequest {

    @NotNull
    private UUID idempotencyKey;

    @NotEmpty
    @Valid
    private List<AnswerRequest> answers;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AnswerRequest {

        @NotNull
        private UUID questionId;

        @NotNull
        private UUID optionId;
    }
}
