package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import com.skillsprint.enums.marketplace.PlatformTreasuryReferenceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformTreasuryEntryResponse {
    UUID entryId;
    PlatformTreasuryAsset asset;
    PlatformTreasuryDirection direction;
    PlatformTreasuryEntryType entryType;
    PlatformTreasuryReferenceType referenceType;
    UUID referenceId;
    BigDecimal amount;
    String actorUserId;
    String actorName;
    String counterpartyUserId;
    String counterpartyName;
    String externalReference;
    String note;
    Map<String, Object> metadata;
    Instant occurredAt;
}
