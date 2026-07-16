package com.skillsprint.service.payment;

import com.skillsprint.configuration.payment.SepayProperties;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.entity.User;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/**
 * The SePay transfer conventions shared by every payment purpose: payment code, expiry,
 * receiving bank account, and QR URL.
 *
 * <p>Purpose-specific fields (service plan, Coin amount) are set by the caller, so the
 * two purposes share the transfer mechanics without sharing their business rules.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SepayPaymentFactory {

    SepayProperties sepayProperties;

    public void requireReady() {
        if (!sepayProperties.ready()) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, "SePay chưa được cấu hình đầy đủ");
        }
    }

    /** A PENDING SePay payment with a fresh code, expiry, and the configured receiving account. */
    public PaymentTransaction newPendingPayment(User user, BigDecimal amount) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setProvider(PaymentProvider.SEPAY);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTxnRef(generatePaymentCode());
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setExpireAt(Instant.now().plusSeconds(sepayProperties.expireMinutesValue() * 60L));
        transaction.setBankCode(sepayProperties.bankCode().trim());
        transaction.setBankAccountNumber(sepayProperties.bankAccountNumber().trim());
        transaction.setBankAccountName(sepayProperties.bankAccountName().trim());
        transaction.setTransferContent(transaction.getTxnRef());
        return transaction;
    }

    public String buildQrCodeUrl(PaymentTransaction transaction) {
        if (sepayProperties.qrBaseUrl() == null || sepayProperties.qrBaseUrl().isBlank()) {
            return null;
        }

        return sepayProperties.qrBaseUrl()
                .replace("{bankCode}", encode(transaction.getBankCode()))
                .replace("{accountNumber}", encode(transaction.getBankAccountNumber()))
                .replace("{amount}", transaction.getAmount().toPlainString())
                .replace("{content}", encode(transaction.getTransferContent()));
    }

    private String generatePaymentCode() {
        return "DH" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6)
                .toUpperCase();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
