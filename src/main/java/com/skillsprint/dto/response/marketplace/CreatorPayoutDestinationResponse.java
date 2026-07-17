package com.skillsprint.dto.response.marketplace;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatorPayoutDestinationResponse {
    UUID destinationId;
    String bankName;
    String bankCode;
    String accountHolder;
    String accountNumberMasked;
    String qrViewUrl;
    Instant updatedAt;
}
