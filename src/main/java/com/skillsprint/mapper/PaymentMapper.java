package com.skillsprint.mapper;

import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentTransactionResponse toResponse(PaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
                .paymentId(transaction.getPaymentId())
                .txnRef(transaction.getTxnRef())
                .provider(transaction.getProvider())
                .status(transaction.getStatus())
                .planId(transaction.getPlan().getPlanId())
                .planName(transaction.getPlan().getPlanName())
                .planType(transaction.getPlan().getPlanType())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .subscriptionMonths(transaction.getSubscriptionMonths())
                .vnpTransactionNo(transaction.getVnpTransactionNo())
                .vnpBankCode(transaction.getVnpBankCode())
                .vnpBankTranNo(transaction.getVnpBankTranNo())
                .vnpCardType(transaction.getVnpCardType())
                .vnpResponseCode(transaction.getVnpResponseCode())
                .vnpTransactionStatus(transaction.getVnpTransactionStatus())
                .expireAt(transaction.getExpireAt())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}