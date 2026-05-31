package com.skillsprint.mapper;

import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentTransactionResponse toResponse(PaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(transaction.getStatus())
                .plan(transaction.getPlan().getPlanType())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentCode(transaction.getTxnRef())
                .qrUrl(transaction.getQrCodeUrl())
                .providerTransactionId(transaction.getProviderTransactionId())
                .providerReferenceCode(transaction.getProviderReferenceCode())
                .expiredAt(transaction.getExpireAt())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
