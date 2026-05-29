package com.skillsprint.dto.response.payment;

import com.skillsprint.enums.payment.PaymentStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VnPayReturnResponse {

    boolean validSignature;
    String responseCode;
    String transactionStatus;
    PaymentStatus paymentStatus;
    String message;
    PaymentTransactionResponse transaction;
}