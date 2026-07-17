package com.skillsprint.dto.response.marketplace;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatorEarningsResponse {
    Integer pendingAmount;
    Integer reservedAmount;
    Integer paidAmount;
    Integer availableAmount;
    List<CreatorEarningResponse> earnings;
}
