package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.CreatorEarningState;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatorEarningResponse {
    UUID earningEntryId;
    UUID settlementId;
    UUID saleId;
    Integer amount;
    Integer availableAmount;
    Integer reservedAmount;
    Integer paidAmount;
    CreatorEarningState state;
    Instant createdAt;
}
