package com.skillsprint.dto.response.marketplace;
import java.time.Instant; import java.util.UUID; import lombok.*;
@Getter @Builder public class MarketplaceChallengeSessionResponse {
    UUID sessionId; UUID packId; UUID versionId; Integer versionNo; Instant startedAt; Instant expiresAt;
}
