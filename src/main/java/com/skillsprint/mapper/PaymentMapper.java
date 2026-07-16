package com.skillsprint.mapper;

import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.dto.response.payment.UserPaymentResponse;
import com.skillsprint.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public UserPaymentResponse toUserResponse(PaymentTransaction transaction) {
        return UserPaymentResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(transaction.getStatus())
                .planId(transaction.getPlan() != null ? transaction.getPlan().getPlanId() : null)
                .plan(transaction.getPlan() != null ? transaction.getPlan().getPlanType() : null)
                .planName(transaction.getPlan() != null ? transaction.getPlan().getPlanName() : null)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentCode(transaction.getTxnRef())
                .qrUrl(transaction.getQrCodeUrl())
                .expiredAt(transaction.getExpireAt())
                .paidAt(transaction.getPaidAt())
                .build();
    }

    public PaymentTransactionResponse toResponse(PaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(transaction.getStatus())
                .purpose(transaction.getPurpose())
                .planId(transaction.getPlan() != null ? transaction.getPlan().getPlanId() : null)
                .plan(transaction.getPlan() != null ? transaction.getPlan().getPlanType() : null)
                .planName(transaction.getPlan() != null ? transaction.getPlan().getPlanName() : null)
                .coinAmount(transaction.getCoinAmount())
                .coinPackageKey(transaction.getCoinPackageKey())
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
