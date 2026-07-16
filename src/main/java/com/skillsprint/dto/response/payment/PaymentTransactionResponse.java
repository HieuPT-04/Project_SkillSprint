package com.skillsprint.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.payment.PaymentPurpose;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransactionResponse {

    UUID paymentId;
    PaymentStatus status;
    PaymentPurpose purpose;

    UUID planId;
    ServicePlanType plan;
    String planName;
    Integer coinAmount;
    String coinPackageKey;

    BigDecimal amount;
    String currency;

    String paymentCode;
    String qrUrl;

    String providerTransactionId;
    String providerReferenceCode;

    Instant expiredAt;
    Instant paidAt;
    Instant createdAt;
    Instant updatedAt;
}
