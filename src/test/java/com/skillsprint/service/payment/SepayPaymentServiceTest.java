package com.skillsprint.service.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.skillsprint.enums.payment.PaymentPurpose;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.skillsprint.service.marketplace.PlatformTreasuryService;

@ExtendWith(MockitoExtension.class)
class SepayPaymentServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ServicePlanRepository servicePlanRepository;

    @Mock
    PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    CoinTopUpService coinTopUpService;

    @Mock
    PlatformTreasuryService platformTreasuryService;

    @Mock
    PaymentMapper paymentMapper;

    SepayPaymentService sepayPaymentService;
    SepayProperties sepayProperties;
    ObjectMapper objectMapper;
    User user;
    ServicePlan builderPlan;

    @BeforeEach
    void setUp() {
        sepayProperties = new SepayProperties(
                true,
                "MBBANK",
                "123 456 789",
                "SKILL SPRINT",
                "webhook-secret",
                "https://qr.example/{bankCode}/{accountNumber}?amount={amount}&content={content}",
                20
        );
        objectMapper = new ObjectMapper();
        sepayPaymentService = service(sepayProperties);
        user = user("user-1");
        builderPlan = plan(ServicePlanType.SKILL_BUILDER, "Skill Builder", "100000");
    }

    @Test
    void createPaymentRejectsIncompleteSepayConfiguration() {
        sepayPaymentService = service(new SepayProperties(
                false,
                "MBBANK",
                "123456789",
                "SKILL SPRINT",
                "webhook-secret",
                "https://qr.example",
                20
        ));
        CreateSepayPaymentRequest request = request(ServicePlanType.SKILL_BUILDER);

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.createPayment("user-1", request)
        );

        assertEquals(ErrorCode.PAYMENT_PROVIDER_ERROR, exception.getErrorCode());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void createPaymentReturnsReusablePendingPayment() {
        PaymentTransaction reusablePayment = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        reusablePayment.setQrCodeUrl("https://existing-qr");
        reusablePayment.setExpireAt(Instant.now().plus(Duration.ofMinutes(10)));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(servicePlanRepository.findByPlanType(ServicePlanType.SKILL_BUILDER)).thenReturn(Optional.of(builderPlan));
        when(paymentTransactionRepository.findFirstByUserUserIdAndPlanPlanIdAndProviderAndStatusAndExpireAtAfterOrderByCreatedAtDesc(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(Optional.of(reusablePayment));

        SepayPaymentResponse response =
                sepayPaymentService.createPayment("user-1", request(ServicePlanType.SKILL_BUILDER));

        assertEquals(reusablePayment.getPaymentId(), response.getPaymentId());
        assertEquals("DH123", response.getPaymentCode());
        assertEquals("https://existing-qr", response.getQrUrl());
        verify(subscriptionService, never()).calculatePaymentAmount(any(), any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void createPaymentCreatesPendingTransactionWithRoundedAmountAndQrUrl() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(servicePlanRepository.findByPlanType(ServicePlanType.SKILL_BUILDER)).thenReturn(Optional.of(builderPlan));
        when(paymentTransactionRepository.findFirstByUserUserIdAndPlanPlanIdAndProviderAndStatusAndExpireAtAfterOrderByCreatedAtDesc(
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(Optional.empty());
        when(subscriptionService.calculatePaymentAmount("user-1", builderPlan))
                .thenReturn(new BigDecimal("123456.50"));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> {
                    PaymentTransaction transaction = invocation.getArgument(0);
                    if (transaction.getPaymentId() == null) {
                        transaction.setPaymentId(UUID.randomUUID());
                    }
                    return transaction;
                });

        SepayPaymentResponse response =
                sepayPaymentService.createPayment("user-1", request(ServicePlanType.SKILL_BUILDER));

        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("123457"), response.getAmount());
        assertEquals("VND", response.getCurrency());
        assertTrue(response.getPaymentCode().startsWith("DH"));
        assertTrue(response.getQrUrl().contains("MBBANK"));
        assertTrue(response.getQrUrl().contains("amount=123457"));
        assertTrue(response.getQrUrl().contains("content=" + response.getPaymentCode()));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        PaymentTransaction savedTransaction = captor.getAllValues().get(1);
        assertSame(user, savedTransaction.getUser());
        assertSame(builderPlan, savedTransaction.getPlan());
        assertEquals(PaymentProvider.SEPAY, savedTransaction.getProvider());
        assertEquals("123 456 789", savedTransaction.getBankAccountNumber());
        assertEquals(savedTransaction.getTxnRef(), savedTransaction.getTransferContent());
        assertNotNull(savedTransaction.getExpireAt());
        assertEquals(PaymentPurpose.SUBSCRIPTION, savedTransaction.getPurpose());
        assertEquals(1, savedTransaction.getSubscriptionMonths());
        assertNull(savedTransaction.getCoinAmount());
        assertNull(savedTransaction.getCoinPackageKey());
    }

    @Test
    void createPaymentRejectsFreePlanAsNotPayable() {
        ServicePlan freePlan = plan(ServicePlanType.FREE, "Free", "0");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(servicePlanRepository.findByPlanType(ServicePlanType.FREE)).thenReturn(Optional.of(freePlan));

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.createPayment("user-1", request(ServicePlanType.FREE))
        );

        assertEquals(ErrorCode.PAYMENT_PLAN_NOT_PAYABLE, exception.getErrorCode());
    }

    @Test
    void handleWebhookRejectsInvalidApiKeyWhenConfigured() {
        SepayWebhookRequest request = webhook("DH123", "100000");

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.handleWebhook(request, null, "wrong")
        );

        assertEquals(ErrorCode.PAYMENT_INVALID_SIGNATURE, exception.getErrorCode());
        verify(paymentTransactionRepository, never()).findByProviderTransactionId(any());
    }

    @Test
    void handleWebhookIgnoresDuplicateProviderTransaction() {
        SepayWebhookRequest request = webhook("DH123", "100000");
        when(paymentTransactionRepository.findByProviderTransactionId("9001"))
                .thenReturn(Optional.of(payment("DH123", PaymentStatus.PAID, builderPlan, "100000")));

        sepayPaymentService.handleWebhook(request, "Bearer webhook-secret", null);

        verify(paymentTransactionRepository, never()).findWithLockByTxnRef(any());
        verify(subscriptionService, never()).activatePaidPlan(any(), any(ServicePlan.class));
    }

    @Test
    void handleWebhookMarksPendingPaymentPaidAndActivatesSubscription() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH123")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);

        sepayPaymentService.handleWebhook(webhook("dh123", "100000.40"), "ApiKey webhook-secret", null);

        assertEquals(PaymentStatus.PAID, transaction.getStatus());
        assertEquals("9001", transaction.getProviderTransactionId());
        assertEquals("BANK-REF-1", transaction.getProviderReferenceCode());
        assertNotNull(transaction.getPaidAt());
        assertTrue(transaction.getRawCallbackData().contains("\"id\":9001"));
        verify(paymentTransactionRepository).save(transaction);
        verify(subscriptionService).activatePaidPlan("user-1", builderPlan);
        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
    }

    @Test
    void handleWebhookCreditsCoinForTopUpPaymentWithoutTouchingSubscriptionState() {
        PaymentTransaction transaction = topUpPayment("DH900", PaymentStatus.PENDING, "19000", 100);
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH900")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);

        sepayPaymentService.handleWebhook(webhook("DH900", "19000"), "Bearer webhook-secret", null);

        assertEquals(PaymentStatus.PAID, transaction.getStatus());
        assertNotNull(transaction.getPaidAt());
        verify(coinTopUpService).creditVerifiedTopUp(transaction);
        verify(subscriptionService, never()).activatePaidPlan(any(), any(ServicePlan.class));
    }

    @Test
    void handleWebhookDoesNotCreditCoinForAnExpiredTopUp() {
        PaymentTransaction transaction = topUpPayment("DH900", PaymentStatus.PENDING, "19000", 100);
        transaction.setExpireAt(Instant.now().minus(Duration.ofSeconds(1)));
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH900")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);

        sepayPaymentService.handleWebhook(webhook("DH900", "19000"), "Bearer webhook-secret", null);

        assertEquals(PaymentStatus.EXPIRED, transaction.getStatus());
        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
    }

    @Test
    void handleWebhookDoesNotCreditCoinWhenTopUpAmountIsWrong() {
        PaymentTransaction transaction = topUpPayment("DH900", PaymentStatus.PENDING, "19000", 100);
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH900")).thenReturn(Optional.of(transaction));

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.handleWebhook(webhook("DH900", "1000"), "Bearer webhook-secret", null)
        );

        assertEquals(ErrorCode.PAYMENT_INVALID_AMOUNT, exception.getErrorCode());
        assertEquals(PaymentStatus.PENDING, transaction.getStatus());
        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void handleWebhookDoesNotCreditCoinWhenReceiverAccountIsWrong() {
        PaymentTransaction transaction = topUpPayment("DH900", PaymentStatus.PENDING, "19000", 100);
        SepayWebhookRequest request = webhook("DH900", "19000");
        request.setAccountNumber("999999");
        request.setSubAccount(null);
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH900")).thenReturn(Optional.of(transaction));

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.handleWebhook(request, "Bearer webhook-secret", null)
        );

        assertEquals(ErrorCode.PAYMENT_INVALID_RECEIVER_ACCOUNT, exception.getErrorCode());
        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
    }

    @Test
    void handleWebhookDoesNotCreditCoinWhenAuthenticationIsInvalid() {
        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.handleWebhook(webhook("DH900", "19000"), null, "wrong")
        );

        assertEquals(ErrorCode.PAYMENT_INVALID_SIGNATURE, exception.getErrorCode());
        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
    }

    @Test
    void repeatedWebhookForAnAlreadyPaidTopUpDoesNotCreditAgain() {
        // Same provider transaction delivered twice.
        when(paymentTransactionRepository.findByProviderTransactionId("9001"))
                .thenReturn(Optional.of(topUpPayment("DH900", PaymentStatus.PAID, "19000", 100)));

        sepayPaymentService.handleWebhook(webhook("DH900", "19000"), "Bearer webhook-secret", null);

        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
        verify(paymentTransactionRepository, never()).findWithLockByTxnRef(any());
    }

    @Test
    void redeliveredWebhookForAPaymentAlreadyMarkedPaidDoesNotCreditAgain() {
        // A different provider transaction id, but the payment already left PENDING.
        PaymentTransaction transaction = topUpPayment("DH900", PaymentStatus.PAID, "19000", 100);
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH900")).thenReturn(Optional.of(transaction));

        sepayPaymentService.handleWebhook(webhook("DH900", "19000"), "Bearer webhook-secret", null);

        verify(coinTopUpService, never()).creditVerifiedTopUp(any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void handleWebhookCanFindPaymentCodeFromTransferContent() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        SepayWebhookRequest request = webhook(null, "100000");
        request.setContent("Thanh toan goi hoc DH123");
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findByProviderAndStatusOrderByCreatedAtDesc(
                PaymentProvider.SEPAY,
                PaymentStatus.PENDING
        )).thenReturn(List.of(transaction));
        when(paymentTransactionRepository.findWithLockByPaymentId(transaction.getPaymentId()))
                .thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);

        sepayPaymentService.handleWebhook(request, null, "webhook-secret");

        assertEquals(PaymentStatus.PAID, transaction.getStatus());
        verify(subscriptionService).activatePaidPlan("user-1", builderPlan);
    }

    @Test
    void handleWebhookMarksExpiredPaymentExpiredWithoutActivatingSubscription() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        transaction.setExpireAt(Instant.now().minus(Duration.ofSeconds(1)));
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH123")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);

        sepayPaymentService.handleWebhook(webhook("DH123", "100000"), "Bearer webhook-secret", null);

        assertEquals(PaymentStatus.EXPIRED, transaction.getStatus());
        assertEquals("9001", transaction.getProviderTransactionId());
        assertNotNull(transaction.getRawCallbackData());
        verify(subscriptionService, never()).activatePaidPlan(any(), any(ServicePlan.class));
    }

    @Test
    void handleWebhookRejectsInvalidReceiverAccount() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        SepayWebhookRequest request = webhook("DH123", "100000");
        request.setAccountNumber("999999");
        request.setSubAccount(null);
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH123")).thenReturn(Optional.of(transaction));

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.handleWebhook(request, "Bearer webhook-secret", null)
        );

        assertEquals(ErrorCode.PAYMENT_INVALID_RECEIVER_ACCOUNT, exception.getErrorCode());
        assertEquals(PaymentStatus.PENDING, transaction.getStatus());
        verify(paymentTransactionRepository, never()).save(any());
        verify(subscriptionService, never()).activatePaidPlan(any(), any(ServicePlan.class));
    }

    @Test
    void handleWebhookRejectsInvalidAmount() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        when(paymentTransactionRepository.findByProviderTransactionId("9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findWithLockByTxnRef("DH123")).thenReturn(Optional.of(transaction));

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.handleWebhook(webhook("DH123", "99999"), "Bearer webhook-secret", null)
        );

        assertEquals(ErrorCode.PAYMENT_INVALID_AMOUNT, exception.getErrorCode());
        assertEquals(PaymentStatus.PENDING, transaction.getStatus());
        verify(paymentTransactionRepository, never()).save(any());
        verify(subscriptionService, never()).activatePaidPlan(any(), any(ServicePlan.class));
    }

    @Test
    void expirePendingPaymentsMarksExpiredSepayPayments() {
        PaymentTransaction first = payment("DH1", PaymentStatus.PENDING, builderPlan, "100000");
        PaymentTransaction second = payment("DH2", PaymentStatus.PENDING, builderPlan, "100000");
        when(paymentTransactionRepository.findByProviderAndStatusAndExpireAtBefore(
                any(),
                any(),
                any()
        )).thenReturn(List.of(first, second));
        when(paymentTransactionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        sepayPaymentService.expirePendingPayments();

        assertEquals(PaymentStatus.EXPIRED, first.getStatus());
        assertEquals(PaymentStatus.EXPIRED, second.getStatus());
        verify(paymentTransactionRepository).saveAll(List.of(first, second));
    }

    @Test
    void getMyPaymentReturnsMappedPaymentForOwnerOnly() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        UserPaymentResponse expected = UserPaymentResponse.builder().paymentId(transaction.getPaymentId()).build();
        when(paymentTransactionRepository.findById(transaction.getPaymentId())).thenReturn(Optional.of(transaction));
        when(paymentMapper.toUserResponse(transaction)).thenReturn(expected);

        UserPaymentResponse response = sepayPaymentService.getMyPayment("user-1", transaction.getPaymentId());

        assertSame(expected, response);
    }

    @Test
    void getMyPaymentRejectsPaymentOwnedByAnotherUser() {
        PaymentTransaction transaction = payment("DH123", PaymentStatus.PENDING, builderPlan, "100000");
        when(paymentTransactionRepository.findById(transaction.getPaymentId())).thenReturn(Optional.of(transaction));

        AppException exception = assertThrows(
                AppException.class,
                () -> sepayPaymentService.getMyPayment("other-user", transaction.getPaymentId())
        );

        assertEquals(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND, exception.getErrorCode());
        verify(paymentMapper, never()).toUserResponse(any());
    }

    @Test
    void getMyPaymentsMapsNewestPayments() {
        PaymentTransaction first = payment("DH1", PaymentStatus.PAID, builderPlan, "100000");
        PaymentTransaction second = payment("DH2", PaymentStatus.PENDING, builderPlan, "100000");
        UserPaymentResponse firstResponse = UserPaymentResponse.builder().paymentId(first.getPaymentId()).build();
        UserPaymentResponse secondResponse = UserPaymentResponse.builder().paymentId(second.getPaymentId()).build();
        when(paymentTransactionRepository.findByUserUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(first, second));
        when(paymentMapper.toUserResponse(first)).thenReturn(firstResponse);
        when(paymentMapper.toUserResponse(second)).thenReturn(secondResponse);

        List<UserPaymentResponse> response = sepayPaymentService.getMyPayments("user-1");

        assertEquals(List.of(firstResponse, secondResponse), response);
    }

    private SepayPaymentService service(SepayProperties properties) {
        return new SepayPaymentService(
                properties,
                new SepayPaymentFactory(properties),
                userRepository,
                servicePlanRepository,
                paymentTransactionRepository,
                subscriptionService,
                coinTopUpService,
                platformTreasuryService,
                paymentMapper,
                objectMapper
        );
    }

    private CreateSepayPaymentRequest request(ServicePlanType planType) {
        CreateSepayPaymentRequest request = new CreateSepayPaymentRequest();
        request.setPlanType(planType);
        return request;
    }

    private SepayWebhookRequest webhook(String code, String amount) {
        SepayWebhookRequest request = new SepayWebhookRequest();
        request.setId(9001L);
        request.setAccountNumber("123456789");
        request.setCode(code);
        request.setContent(code == null ? "" : "Thanh toan " + code);
        request.setDescription("Payment " + (code == null ? "" : code));
        request.setReferenceCode("BANK-REF-1");
        request.setTransferType("in");
        request.setTransferAmount(new BigDecimal(amount));
        return request;
    }

    private PaymentTransaction payment(
            String txnRef,
            PaymentStatus status,
            ServicePlan plan,
            String amount
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPaymentId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setPlan(plan);
        transaction.setProvider(PaymentProvider.SEPAY);
        transaction.setStatus(status);
        transaction.setTxnRef(txnRef);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("VND");
        transaction.setSubscriptionMonths(1);
        transaction.setExpireAt(Instant.now().plus(Duration.ofMinutes(15)));
        transaction.setBankCode("MBBANK");
        transaction.setBankAccountNumber("123 456 789");
        transaction.setBankAccountName("SKILL SPRINT");
        transaction.setTransferContent(txnRef);
        return transaction;
    }

    private PaymentTransaction topUpPayment(String txnRef, PaymentStatus status, String amount, int coinAmount) {
        PaymentTransaction transaction = payment(txnRef, status, null, amount);
        transaction.setPurpose(PaymentPurpose.COIN_TOP_UP);
        transaction.setSubscriptionMonths(0);
        transaction.setCoinAmount(coinAmount);
        transaction.setCoinPackageKey("COIN_" + coinAmount);
        return transaction;
    }

    private ServicePlan plan(ServicePlanType planType, String name, String monthlyPrice) {
        ServicePlan plan = new ServicePlan();
        plan.setPlanId(UUID.randomUUID());
        plan.setPlanType(planType);
        plan.setPlanName(name);
        plan.setMonthlyPrice(new BigDecimal(monthlyPrice));
        plan.setCurrency("VND");
        plan.setActive(true);
        plan.setPublicVisible(true);
        return plan;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
