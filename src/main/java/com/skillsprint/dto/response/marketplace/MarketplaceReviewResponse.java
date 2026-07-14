package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import lombok.*;

@Getter @Builder
public class MarketplaceReviewResponse { String userName; Integer rating; String comment; Instant updatedAt; }
