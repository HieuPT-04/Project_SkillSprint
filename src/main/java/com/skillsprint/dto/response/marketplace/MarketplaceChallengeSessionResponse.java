package com.skillsprint.dto.response.marketplace;
import java.time.Instant; import java.util.UUID; import lombok.*;
@Getter @Builder public class MarketplaceChallengeSessionResponse { UUID sessionId; Instant startedAt; Instant expiresAt; }
