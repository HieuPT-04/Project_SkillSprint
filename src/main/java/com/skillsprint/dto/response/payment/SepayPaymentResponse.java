package com.skillsprint.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SepayPaymentResponse {

    UUID paymentId;
    PaymentStatus status;

    UUID planId;
    ServicePlanType plan;
    String planName;

    BigDecimal amount;
    String currency;

    String paymentCode;
    String qrUrl;
    BankInfo bank;

    Instant expiredAt;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class BankInfo {

        String bankCode;
        String accountNumber;
        String accountName;
    }
}
