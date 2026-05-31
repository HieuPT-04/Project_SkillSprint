package com.skillsprint.dto.response.payment;

import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
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
public class PaymentTransactionResponse {

    UUID paymentId;
    String txnRef;

    PaymentProvider provider;
    PaymentStatus status;

    UUID planId;
    String planName;
    ServicePlanType planType;

    BigDecimal amount;
    String currency;
    Integer subscriptionMonths;

    String qrCodeUrl;
    String providerTransactionId;
    String providerReferenceCode;

    Instant expireAt;
    Instant paidAt;
    Instant createdAt;
    Instant updatedAt;
}
