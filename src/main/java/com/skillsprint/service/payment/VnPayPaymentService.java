package com.skillsprint.service.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.payment.VnPayProperties;
import com.skillsprint.dto.request.payment.CreateVnPayPaymentRequest;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.dto.response.payment.VnPayIpnResponse;
import com.skillsprint.dto.response.payment.VnPayPaymentUrlResponse;
import com.skillsprint.dto.response.payment.VnPayReturnResponse;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VnPayPaymentService {

    static ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    VnPayProperties vnPayProperties;
    VnPaySigner vnPaySigner;
    UserRepository userRepository;
    ServicePlanRepository servicePlanRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    SubscriptionService subscriptionService;
    PaymentMapper paymentMapper;
    ObjectMapper objectMapper;

    @Transactional
    public VnPayPaymentUrlResponse createPaymentUrl(
            String userId,
            CreateVnPayPaymentRequest request,
            String ipAddress
    ) {
        if (!vnPayProperties.ready()) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, "VNPay chưa được cấu hình đầy đủ");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        ServicePlan plan = servicePlanRepository.findByPlanType(request.getPlanType())
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));

        if (!plan.isActive()) {
            throw new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND);
        }

        if (ServicePlanType.FREE.equals(plan.getPlanType())
                || plan.getMonthlyPrice() == null
                || plan.getMonthlyPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.PAYMENT_PLAN_NOT_PAYABLE);
        }

        int months = request.getMonths() == null ? 1 : request.getMonths();
        BigDecimal amount = plan.getMonthlyPrice()
                .multiply(BigDecimal.valueOf(months))
                .setScale(0, RoundingMode.HALF_UP);

        String txnRef = generateTxnRef();
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        LocalDateTime expireDate = now.plusMinutes(vnPayProperties.expireMinutesValue());

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setUser(user);
        transaction.setPlan(plan);
        transaction.setProvider(PaymentProvider.VNPAY);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setTxnRef(txnRef);
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setSubscriptionMonths(months);
        transaction.setExpireAt(expireDate.atZone(VN_ZONE).toInstant());

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        Map<String, String> params = buildPaymentParams(
                savedTransaction,
                plan,
                amount,
                now,
                expireDate,
                normalizeLocale(request.getLocale()),
                request.getBankCode(),
                ipAddress
        );

        String paymentUrl = vnPaySigner.buildPaymentUrl(
                vnPayProperties.payUrl(),
                params,
                vnPayProperties.hashSecret()
        );

        savedTransaction.setPaymentUrl(paymentUrl);
        paymentTransactionRepository.save(savedTransaction);

        return VnPayPaymentUrlResponse.builder()
                .paymentId(savedTransaction.getPaymentId())
                .txnRef(savedTransaction.getTxnRef())
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .planType(plan.getPlanType())
                .amount(savedTransaction.getAmount())
                .currency(savedTransaction.getCurrency())
                .subscriptionMonths(savedTransaction.getSubscriptionMonths())
                .paymentUrl(paymentUrl)
                .expireAt(savedTransaction.getExpireAt())
                .build();
    }

    @Transactional
    public VnPayIpnResponse handleIpn(Map<String, String> params) {
        try {
            if (!vnPaySigner.verify(params, vnPayProperties.hashSecret())) {
                return new VnPayIpnResponse("97", "Invalid Checksum");
            }

            String txnRef = params.get("vnp_TxnRef");
            PaymentTransaction transaction = paymentTransactionRepository.findByTxnRef(txnRef)
                    .orElse(null);

            if (transaction == null) {
                return new VnPayIpnResponse("01", "Order not Found");
            }

            if (!isValidAmount(transaction, params)) {
                return new VnPayIpnResponse("04", "Invalid Amount");
            }

            if (!PaymentStatus.PENDING.equals(transaction.getStatus())) {
                return new VnPayIpnResponse("02", "Order already confirmed");
            }

            confirmTransaction(transaction, params);

            return new VnPayIpnResponse("00", "Confirm Success");
        } catch (Exception ex) {
            log.error("[VNPAY] IPN handling failed", ex);
            return new VnPayIpnResponse("99", "Unknown error");
        }
    }

    @Transactional
    public VnPayReturnResponse handleReturn(Map<String, String> params) {
        boolean validSignature = vnPaySigner.verify(params, vnPayProperties.hashSecret());
        String txnRef = params.get("vnp_TxnRef");

        PaymentTransaction transaction = txnRef == null
                ? null
                : paymentTransactionRepository.findByTxnRef(txnRef).orElse(null);

        if (!validSignature) {
            return VnPayReturnResponse.builder()
                    .validSignature(false)
                    .responseCode(params.get("vnp_ResponseCode"))
                    .transactionStatus(params.get("vnp_TransactionStatus"))
                    .paymentStatus(transaction == null ? null : transaction.getStatus())
                    .message("Chữ ký VNPay không hợp lệ")
                    .transaction(transaction == null ? null : paymentMapper.toResponse(transaction))
                    .build();
        }

        if (transaction != null
                && PaymentStatus.PENDING.equals(transaction.getStatus())
                && isValidAmount(transaction, params)) {
            confirmTransaction(transaction, params);
        }

        PaymentTransaction refreshedTransaction = txnRef == null
                ? transaction
                : paymentTransactionRepository.findByTxnRef(txnRef).orElse(transaction);

        return VnPayReturnResponse.builder()
                .validSignature(true)
                .responseCode(params.get("vnp_ResponseCode"))
                .transactionStatus(params.get("vnp_TransactionStatus"))
                .paymentStatus(refreshedTransaction == null ? null : refreshedTransaction.getStatus())
                .message(resolveReturnMessage(params))
                .transaction(refreshedTransaction == null ? null : paymentMapper.toResponse(refreshedTransaction))
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getMyPayments(String userId) {
        return paymentTransactionRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse getMyPayment(String userId, UUID paymentId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(paymentId)
                .filter(payment -> payment.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND));

        return paymentMapper.toResponse(transaction);
    }

    private Map<String, String> buildPaymentParams(
            PaymentTransaction transaction,
            ServicePlan plan,
            BigDecimal amount,
            LocalDateTime createDate,
            LocalDateTime expireDate,
            String locale,
            String bankCode,
            String ipAddress
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayProperties.tmnCode());
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        params.put("vnp_CreateDate", createDate.format(VNPAY_DATE_FORMAT));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_Locale", locale);
        params.put("vnp_OrderInfo", buildOrderInfo(transaction, plan));
        params.put("vnp_OrderType", "other");
        params.put("vnp_ReturnUrl", vnPayProperties.returnUrl());
        params.put("vnp_TxnRef", transaction.getTxnRef());
        params.put("vnp_ExpireDate", expireDate.format(VNPAY_DATE_FORMAT));

        if (bankCode != null && !bankCode.isBlank()) {
            params.put("vnp_BankCode", bankCode.trim());
        }

        return params;
    }

    private void confirmTransaction(PaymentTransaction transaction, Map<String, String> params) {
        fillVnPayFields(transaction, params);

        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));

        if (success) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            transaction.setPaidAt(resolvePayDate(params));
            paymentTransactionRepository.save(transaction);

            subscriptionService.activatePaidPlan(
                    transaction.getUser().getUserId(),
                    transaction.getPlan().getPlanType(),
                    transaction.getSubscriptionMonths()
            );
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(transaction);
        }
    }

    private void fillVnPayFields(PaymentTransaction transaction, Map<String, String> params) {
        transaction.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        transaction.setVnpBankCode(params.get("vnp_BankCode"));
        transaction.setVnpBankTranNo(params.get("vnp_BankTranNo"));
        transaction.setVnpCardType(params.get("vnp_CardType"));
        transaction.setVnpResponseCode(params.get("vnp_ResponseCode"));
        transaction.setVnpTransactionStatus(params.get("vnp_TransactionStatus"));
        transaction.setVnpSecureHash(params.get("vnp_SecureHash"));
        transaction.setRawCallbackData(toJson(params));
    }

    private boolean isValidAmount(PaymentTransaction transaction, Map<String, String> params) {
        String vnpAmount = params.get("vnp_Amount");
        if (vnpAmount == null || vnpAmount.isBlank()) {
            return false;
        }

        BigDecimal expectedAmountX100 = transaction.getAmount().multiply(BigDecimal.valueOf(100));
        return expectedAmountX100.compareTo(new BigDecimal(vnpAmount)) == 0;
    }

    private Instant resolvePayDate(Map<String, String> params) {
        String payDate = params.get("vnp_PayDate");
        if (payDate == null || payDate.isBlank()) {
            return Instant.now();
        }

        try {
            return LocalDateTime.parse(payDate, VNPAY_DATE_FORMAT)
                    .atZone(VN_ZONE)
                    .toInstant();
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private String resolveReturnMessage(Map<String, String> params) {
        if ("00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"))) {
            return "Thanh toán thành công";
        }
        return "Thanh toán thất bại hoặc bị hủy";
    }

    private String buildOrderInfo(PaymentTransaction transaction, ServicePlan plan) {
        return "SkillSprint subscription " + plan.getPlanName() + " txn " + transaction.getTxnRef();
    }

    private String normalizeLocale(String locale) {
        if ("en".equalsIgnoreCase(locale)) {
            return "en";
        }
        return "vn";
    }

    private String generateTxnRef() {
        return "SS" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}