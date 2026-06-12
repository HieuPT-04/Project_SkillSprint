package com.skillsprint.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.payment.SepayProperties;
import com.skillsprint.dto.request.payment.CreateSepayPaymentRequest;
import com.skillsprint.dto.request.payment.SepayWebhookRequest;
import com.skillsprint.dto.response.payment.SepayPaymentResponse;
import com.skillsprint.dto.response.payment.UserPaymentResponse;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.User;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.PaymentMapper;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.ServicePlanRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.subscription.SubscriptionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SepayPaymentService {

    static final int SUBSCRIPTION_MONTHS = 1;

    SepayProperties sepayProperties;
    UserRepository userRepository;
    ServicePlanRepository servicePlanRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    SubscriptionService subscriptionService;
    PaymentMapper paymentMapper;
    ObjectMapper objectMapper;

    @Transactional
    public SepayPaymentResponse createPayment(String userId, CreateSepayPaymentRequest request) {
        if (!sepayProperties.ready()) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, "SePay chưa được cấu hình đầy đủ");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
        ServicePlan plan = getPayablePlan(request);

        PaymentTransaction reusablePayment = findReusablePendingPayment(userId, plan);
        if (reusablePayment != null) {
            return toSepayPaymentResponse(reusablePayment);
        }

        BigDecimal amount = subscriptionService.calculatePaymentAmount(userId, plan)
                .multiply(BigDecimal.valueOf(SUBSCRIPTION_MONTHS))
                .setScale(0, RoundingMode.HALF_UP);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setPlan(plan);
        transaction.setProvider(PaymentProvider.SEPAY);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTxnRef(generatePaymentCode());
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setSubscriptionMonths(SUBSCRIPTION_MONTHS);
        transaction.setExpireAt(Instant.now().plusSeconds(sepayProperties.expireMinutesValue() * 60L));
        transaction.setBankCode(sepayProperties.bankCode().trim());
        transaction.setBankAccountNumber(sepayProperties.bankAccountNumber().trim());
        transaction.setBankAccountName(sepayProperties.bankAccountName().trim());
        transaction.setTransferContent(transaction.getTxnRef());

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
        savedTransaction.setQrCodeUrl(buildQrCodeUrl(savedTransaction));

        return toSepayPaymentResponse(paymentTransactionRepository.save(savedTransaction));
    }

    @Transactional
    public void handleWebhook(SepayWebhookRequest request, String authorizationHeader, String apiKeyHeader) {
        validateWebhookAuthentication(authorizationHeader, apiKeyHeader);

        if (request == null || request.getId() == null) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, "Webhook SePay thiếu mã giao dịch");
        }

        String providerTransactionId = String.valueOf(request.getId());
        if (paymentTransactionRepository.findByProviderTransactionId(providerTransactionId).isPresent()) {
            return;
        }

        PaymentTransaction transaction = findTransactionFromWebhook(request);
        if (!PaymentStatus.PENDING.equals(transaction.getStatus())) {
            return;
        }

        String rawCallbackData = toJson(request);

        if (isExpired(transaction)) {
            transaction.setRawCallbackData(rawCallbackData);
            transaction.setProviderTransactionId(providerTransactionId);
            transaction.setProviderReferenceCode(request.getReferenceCode());
            transaction.setStatus(PaymentStatus.EXPIRED);
            paymentTransactionRepository.save(transaction);
            return;
        }

        // Validate before dirtying the entity so that if validation fails
        // the transaction rolls back with no dirty state, letting AppException
        // propagate cleanly to the global handler (→ 400, not 500).
        validateIncomingPayment(request, transaction);

        transaction.setRawCallbackData(rawCallbackData);
        transaction.setProviderTransactionId(providerTransactionId);
        transaction.setProviderReferenceCode(request.getReferenceCode());
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setPaidAt(Instant.now());
        paymentTransactionRepository.save(transaction);

        subscriptionService.activatePaidPlan(
                transaction.getUser().getUserId(),
                transaction.getPlan()
        );
    }

    @Transactional(readOnly = true)
    public List<UserPaymentResponse> getMyPayments(String userId) {
        return paymentTransactionRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(paymentMapper::toUserResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserPaymentResponse getMyPayment(String userId, UUID paymentId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(paymentId)
                .filter(payment -> payment.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        return paymentMapper.toUserResponse(transaction);
    }

    @Scheduled(fixedDelayString = "${app.payment.sepay.expire-fixed-delay-ms:60000}")
    @Transactional
    public void expirePendingPayments() {
        List<PaymentTransaction> expiredPayments = paymentTransactionRepository
                .findByProviderAndStatusAndExpireAtBefore(
                        PaymentProvider.SEPAY,
                        PaymentStatus.PENDING,
                        Instant.now()
                );

        expiredPayments.forEach(payment -> payment.setStatus(PaymentStatus.EXPIRED));
        if (!expiredPayments.isEmpty()) {
            paymentTransactionRepository.saveAll(expiredPayments);
            log.info("[SEPAY] Expired {} pending payments", expiredPayments.size());
        }
    }

    private ServicePlan getPayablePlan(CreateSepayPaymentRequest request) {
        ServicePlan plan;

        if (request.getPlanId() != null) {
            plan = servicePlanRepository.findById(request.getPlanId())
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));
        } else if (request.getPlanType() != null) {
            plan = servicePlanRepository.findByPlanType(request.getPlanType())
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));
        } else {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Cần chọn gói thanh toán");
        }

        if (!plan.isActive() || Boolean.FALSE.equals(plan.getPublicVisible())) {
            throw new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND);
        }

        if (ServicePlanType.FREE.equals(plan.getPlanType())
                || plan.getMonthlyPrice() == null
                || plan.getMonthlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.PAYMENT_PLAN_NOT_PAYABLE);
        }

        return plan;
    }

    private PaymentTransaction findReusablePendingPayment(String userId, ServicePlan plan) {
        return paymentTransactionRepository
                .findFirstByUserUserIdAndPlanPlanIdAndProviderAndStatusAndExpireAtAfterOrderByCreatedAtDesc(
                        userId,
                        plan.getPlanId(),
                        PaymentProvider.SEPAY,
                        PaymentStatus.PENDING,
                        Instant.now()
                )
                .orElse(null);
    }

    private PaymentTransaction findTransactionFromWebhook(SepayWebhookRequest request) {
        String paymentCode = normalizePaymentCode(request.getCode());
        if (paymentCode != null) {
            PaymentTransaction exactPayment = paymentTransactionRepository.findWithLockByTxnRef(paymentCode)
                    .orElse(null);
            if (exactPayment != null) {
                return exactPayment;
            }
        }

        String searchableText = webhookSearchableText(request);
        PaymentTransaction matchedPayment = paymentTransactionRepository.findByProviderAndStatusOrderByCreatedAtDesc(
                        PaymentProvider.SEPAY,
                        PaymentStatus.PENDING
                )
                .stream()
                .filter(payment -> searchableText.contains(payment.getTxnRef()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        return paymentTransactionRepository.findWithLockByPaymentId(matchedPayment.getPaymentId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));
    }

    private void validateIncomingPayment(SepayWebhookRequest request, PaymentTransaction transaction) {
        if (!"in".equalsIgnoreCase(request.getTransferType())) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, "Webhook SePay không phải giao dịch nhận tiền");
        }

        if (!sameAccountNumber(request.getAccountNumber(), sepayProperties.bankAccountNumber())
                && !sameAccountNumber(request.getSubAccount(), sepayProperties.bankAccountNumber())) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_RECEIVER_ACCOUNT);
        }

        if (request.getTransferAmount() == null
                || transaction.getAmount().compareTo(request.getTransferAmount().setScale(0, RoundingMode.HALF_UP)) != 0) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_AMOUNT,
                    "Sai lệch số tiền: Hóa đơn yêu cầu " + transaction.getAmount()
                            + " nhưng thực nhận " + request.getTransferAmount());
        }

        String searchableText = webhookSearchableText(request);
        String code = request.getCode() == null ? "" : request.getCode();
        if (!searchableText.contains(transaction.getTxnRef()) && !transaction.getTxnRef().equalsIgnoreCase(code.trim())) {
            throw new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND);
        }
    }

    private String webhookSearchableText(SepayWebhookRequest request) {
        return String.join(" ",
                request.getCode() == null ? "" : request.getCode(),
                request.getContent() == null ? "" : request.getContent(),
                request.getDescription() == null ? "" : request.getDescription(),
                request.getReferenceCode() == null ? "" : request.getReferenceCode()
        ).toUpperCase();
    }

    private void validateWebhookAuthentication(String authorizationHeader, String apiKeyHeader) {
        if (!sepayProperties.hasWebhookApiKey()) {
            return;
        }

        String expectedApiKey = sepayProperties.webhookApiKey().trim();
        if (matchesApiKey(apiKeyHeader, expectedApiKey) || matchesApiKey(authorizationHeader, expectedApiKey)) {
            return;
        }

        throw new AppException(ErrorCode.PAYMENT_INVALID_SIGNATURE);
    }

    private boolean matchesApiKey(String actual, String expected) {
        if (actual == null || actual.isBlank()) {
            return false;
        }

        String value = actual.trim();
        return value.equals(expected)
                || value.equals("Apikey " + expected)
                || value.equals("ApiKey " + expected)
                || value.equals("Bearer " + expected);
    }

    private boolean isExpired(PaymentTransaction transaction) {
        return transaction.getExpireAt() != null && transaction.getExpireAt().isBefore(Instant.now());
    }

    private boolean sameAccountNumber(String actual, String expected) {
        return normalizeAccountNumber(actual).equals(normalizeAccountNumber(expected));
    }

    private String normalizeAccountNumber(String accountNumber) {
        return accountNumber == null ? "" : accountNumber.replaceAll("\\s+", "").trim();
    }

    private String normalizePaymentCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private SepayPaymentResponse toSepayPaymentResponse(PaymentTransaction transaction) {
        return SepayPaymentResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(transaction.getStatus())
                .planId(transaction.getPlan().getPlanId())
                .plan(transaction.getPlan().getPlanType())
                .planName(transaction.getPlan().getPlanName())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentCode(transaction.getTxnRef())
                .qrUrl(transaction.getQrCodeUrl())
                .bank(SepayPaymentResponse.BankInfo.builder()
                        .bankCode(transaction.getBankCode())
                        .accountNumber(transaction.getBankAccountNumber())
                        .accountName(transaction.getBankAccountName())
                        .build())
                .expiredAt(transaction.getExpireAt())
                .build();
    }

    private String buildQrCodeUrl(PaymentTransaction transaction) {
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

    private String toJson(SepayWebhookRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
