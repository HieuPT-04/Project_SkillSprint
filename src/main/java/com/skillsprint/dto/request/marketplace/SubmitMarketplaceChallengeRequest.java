package com.skillsprint.dto.request.marketplace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.*;
@Getter @Setter @NoArgsConstructor
public class SubmitMarketplaceChallengeRequest { @NotNull private UUID sessionId; @NotEmpty @Valid private List<SubmitMarketplaceQuizRequest.AnswerRequest> answers; }
