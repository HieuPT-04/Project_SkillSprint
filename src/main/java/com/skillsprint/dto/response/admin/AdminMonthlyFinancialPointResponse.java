package com.skillsprint.dto.response.admin;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminMonthlyFinancialPointResponse {
    String month;
    BigDecimal subscriptionRevenue;
    BigDecimal coinTopUp;
    long marketplaceCommission;
}
