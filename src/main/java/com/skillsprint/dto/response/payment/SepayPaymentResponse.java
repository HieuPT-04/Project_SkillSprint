package com.skillsprint.dto.response.payment;

import com.skillsprint.enums.plan.ServicePlanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SepayPaymentResponse {

    UUID paymentId;
    String paymentCode;

    ServicePlanType planType;

    BigDecimal amount;
    String currency;
    Integer subscriptionMonths;

    String qrCodeUrl;

    Instant expireAt;
}
