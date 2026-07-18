package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartMarketplacePracticeAttemptRequest {

    @NotNull
    @Min(1)
    private Integer chapterSequenceNo;
}
