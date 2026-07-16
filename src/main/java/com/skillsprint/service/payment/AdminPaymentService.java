package com.skillsprint.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.admin.ReconcilePaymentRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.PaymentMapper;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.service.subscription.SubscriptionService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminPaymentService {

    private static final int MAX_PAGE_SIZE = 100;

    PaymentTransactionRepository paymentTransactionRepository;
    SubscriptionService subscriptionService;
    CoinTopUpService coinTopUpService;
    PaymentMapper paymentMapper;
    ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<PaymentTransactionResponse> getPayments(PaymentStatus status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size)
        );

        Page<PaymentTransactionResponse> payments = paymentTransactionRepository
                .searchAdminPayments(status != null ? status.name() : null, normalizeSearch(search), pageable)
                .map(paymentMapper::toResponse);

        return PageResponse.from(payments);
    }

    @Transactional
    public PaymentTransactionResponse reconcilePayment(UUID paymentId, ReconcilePaymentRequest request) {
        PaymentTransaction transaction = paymentTransactionRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        if (!PaymentProvider.SEPAY.equals(transaction.getProvider())) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }

        if (PaymentStatus.PAID.equals(transaction.getStatus())) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
        }

        String providerTransactionId = request.getProviderTransactionId().trim();
        paymentTransactionRepository.findByProviderTransactionId(providerTransactionId)
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
                });

        Instant paidAt = request.getPaidAt() == null ? Instant.now() : request.getPaidAt();

        transaction.setStatus(PaymentStatus.PAID);
        transaction.setPaidAt(paidAt);
        transaction.setProviderTransactionId(providerTransactionId);
        transaction.setProviderReferenceCode(normalizeBlank(request.getProviderReferenceCode()));
        transaction.setRawCallbackData(toManualReconcileJson(request, paidAt));

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        fulfil(savedTransaction);

        return paymentMapper.toResponse(savedTransaction);
    }

    /**
     * Applies what the payment actually bought, once it is safely marked PAID.
     *
     * <p>A manual reconciliation must reach the same end state as the webhook, so the
     * purpose decides the branch here exactly as it does in {@link SepayPaymentService}:
     * a subscription activates its plan, a Coin top-up credits the wallet through the
     * same idempotent path. Only one branch ever runs for a payment, and a top-up has no
     * plan to activate.
     *
     * <p>Runs inside the caller's transaction, so a failure here rolls back the PAID
     * state and the provider transaction id with it.
     */
    private void fulfil(PaymentTransaction payment) {
        switch (payment.getPurpose()) {
            case SUBSCRIPTION -> subscriptionService.activatePaidPlan(
                    payment.getUser().getUserId(),
                    payment.getPlan()
            );
            case COIN_TOP_UP -> coinTopUpService.creditVerifiedTopUp(payment);
            // A purpose nobody taught this method to fulfil must fail loudly rather than
            // leave the buyer paid but empty-handed.
            default -> throw new AppException(ErrorCode.PAYMENT_PURPOSE_MISMATCH);
        }
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toManualReconcileJson(ReconcilePaymentRequest request, Instant paidAt) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("source", "ADMIN_MANUAL_RECONCILE");
            payload.put("providerTransactionId", request.getProviderTransactionId().trim());
            payload.put("paidAt", paidAt.toString());

            String providerReferenceCode = normalizeBlank(request.getProviderReferenceCode());
            if (providerReferenceCode != null) {
                payload.put("providerReferenceCode", providerReferenceCode);
            }

            String note = normalizeBlank(request.getNote());
            if (note != null) {
                payload.put("note", note);
            }

            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"source\":\"ADMIN_MANUAL_RECONCILE\"}";
        }
    }
}
