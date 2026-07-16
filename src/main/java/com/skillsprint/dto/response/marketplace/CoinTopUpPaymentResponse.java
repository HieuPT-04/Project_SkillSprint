package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/** SePay transfer instructions for a Coin top-up, mirroring the subscription payment shape. */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CoinTopUpPaymentResponse {

    UUID paymentId;
    PaymentPurpose purpose;
    PaymentStatus status;
    String packageKey;
    Integer coinAmount;
    BigDecimal amount;
    String currency;
    String paymentCode;
    String qrUrl;
    BankInfo bank;
    Instant expiredAt;

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class BankInfo {
        String bankCode;
        String accountNumber;
        String accountName;
    }
}
