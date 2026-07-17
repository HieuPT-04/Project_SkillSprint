package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatorPayoutResponse {
    UUID payoutId;
    String creatorUserId;
    String creatorName;
    String creatorEmail;
    Integer requestedAmount;
    CreatorPayoutStatus status;
    String bankName;
    String bankCode;
    String accountHolder;
    String accountNumberMasked;
    String qrViewUrl;
    String adminActorUserId;
    String externalTransferReference;
    String rejectionReason;
    String notes;
    Instant createdAt;
    Instant updatedAt;
}
