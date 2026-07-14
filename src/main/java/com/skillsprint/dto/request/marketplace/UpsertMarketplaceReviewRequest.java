package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class UpsertMarketplaceReviewRequest { @NotNull @Min(1) @Max(5) private Integer rating; @Size(max=2000) private String comment; }
