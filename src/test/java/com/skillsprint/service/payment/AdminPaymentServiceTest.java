package com.skillsprint.service.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.admin.ReconcilePaymentRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
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
import com.skillsprint.service.subscription.SubscriptionService;
import java.math.BigDecimal;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @Mock
    PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    PaymentMapper paymentMapper;

    AdminPaymentService adminPaymentService;
    User user;
    ServicePlan builderPlan;

    @BeforeEach
    void setUp() {
        adminPaymentService = new AdminPaymentService(
                paymentTransactionRepository,
                subscriptionService,
                paymentMapper,
                new ObjectMapper()
        );
        user = user("user-1");
        builderPlan = plan(ServicePlanType.SKILL_BUILDER, "Skill Builder", "100000");
    }

    @Test
    void getPaymentsNormalizesPageSizeAndSearchBeforeMapping() {
        PaymentTransaction transaction = payment(PaymentStatus.PENDING);
        PaymentTransactionResponse mapped = PaymentTransactionResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentTransactionRepository.searchAdminPayments(
                org.mockito.Mockito.eq(PaymentStatus.PENDING.name()),
                org.mockito.Mockito.eq("alice@example.com"),
                org.mockito.Mockito.any(Pageable.class)
        )).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(2);
            return new PageImpl<>(List.of(transaction), pageable, 1);
        });
        when(paymentMapper.toResponse(transaction)).thenReturn(mapped);

        PageResponse<PaymentTransactionResponse> response =
                adminPaymentService.getPayments(PaymentStatus.PENDING, "  alice@example.com  ", -5, 0);

        assertEquals(List.of(mapped), response.getItems());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1, response.getTotalItems());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentTransactionRepository).searchAdminPayments(
                org.mockito.Mockito.eq(PaymentStatus.PENDING.name()),
                org.mockito.Mockito.eq("alice@example.com"),
                pageableCaptor.capture()
        );
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getPaymentsCapsLargePageSizeAndAllowsBlankSearch() {
        when(paymentTransactionRepository.searchAdminPayments(
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any(Pageable.class)
        )).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(2);
            return new PageImpl<>(List.of(), pageable, 0);
        });

        PageResponse<PaymentTransactionResponse> response =
                adminPaymentService.getPayments(null, "   ", 2, 500);

        assertEquals(2, response.getPage());
        assertEquals(100, response.getSize());
    }

    @Test
    void reconcilePaymentRejectsMissingPayment() {
        UUID paymentId = UUID.randomUUID();
        when(paymentTransactionRepository.findWithLockByPaymentId(paymentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> adminPaymentService.reconcilePayment(paymentId, reconcileRequest("BANK-9001"))
        );

        assertEquals(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void reconcilePaymentRejectsAlreadyPaidPayment() {
        UUID paymentId = UUID.randomUUID();
        PaymentTransaction transaction = payment(PaymentStatus.PAID);
        when(paymentTransactionRepository.findWithLockByPaymentId(paymentId)).thenReturn(Optional.of(transaction));

        AppException exception = assertThrows(
                AppException.class,
                () -> adminPaymentService.reconcilePayment(paymentId, reconcileRequest("BANK-9001"))
        );

        assertEquals(ErrorCode.PAYMENT_ALREADY_CONFIRMED, exception.getErrorCode());
        verify(paymentTransactionRepository, never()).save(transaction);
        verify(subscriptionService, never()).activatePaidPlan(org.mockito.Mockito.anyString(), org.mockito.Mockito.any(ServicePlan.class));
    }

    @Test
    void reconcilePaymentRejectsDuplicateProviderTransactionId() {
        UUID paymentId = UUID.randomUUID();
        PaymentTransaction transaction = payment(PaymentStatus.PENDING);
        when(paymentTransactionRepository.findWithLockByPaymentId(paymentId)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.findByProviderTransactionId("BANK-9001"))
                .thenReturn(Optional.of(payment(PaymentStatus.PAID)));

        AppException exception = assertThrows(
                AppException.class,
                () -> adminPaymentService.reconcilePayment(paymentId, reconcileRequest(" BANK-9001 "))
        );

        assertEquals(ErrorCode.PAYMENT_ALREADY_CONFIRMED, exception.getErrorCode());
        verify(paymentTransactionRepository, never()).save(transaction);
        verify(subscriptionService, never()).activatePaidPlan(org.mockito.Mockito.anyString(), org.mockito.Mockito.any(ServicePlan.class));
    }

    @Test
    void reconcilePaymentMarksTransactionPaidActivatesPlanAndReturnsMappedResponse() {
        UUID paymentId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-06-23T10:00:00Z");
        PaymentTransaction transaction = payment(PaymentStatus.PENDING);
        ReconcilePaymentRequest request = reconcileRequest(" BANK-9001 ");
        request.setPaidAt(paidAt);
        request.setProviderReferenceCode(" REF-1 ");
        request.setNote(" Admin checked bank statement ");
        PaymentTransactionResponse mapped = PaymentTransactionResponse.builder()
                .paymentId(transaction.getPaymentId())
                .status(PaymentStatus.PAID)
                .build();
        when(paymentTransactionRepository.findWithLockByPaymentId(paymentId)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.findByProviderTransactionId("BANK-9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);
        when(paymentMapper.toResponse(transaction)).thenReturn(mapped);

        PaymentTransactionResponse response = adminPaymentService.reconcilePayment(paymentId, request);

        assertSame(mapped, response);
        assertEquals(PaymentStatus.PAID, transaction.getStatus());
        assertEquals(paidAt, transaction.getPaidAt());
        assertEquals("BANK-9001", transaction.getProviderTransactionId());
        assertEquals("REF-1", transaction.getProviderReferenceCode());
        assertNotNull(transaction.getRawCallbackData());
        assertTrue(transaction.getRawCallbackData().contains("\"source\":\"ADMIN_MANUAL_RECONCILE\""));
        assertTrue(transaction.getRawCallbackData().contains("\"providerTransactionId\":\"BANK-9001\""));
        assertTrue(transaction.getRawCallbackData().contains("\"providerReferenceCode\":\"REF-1\""));
        assertTrue(transaction.getRawCallbackData().contains("\"note\":\"Admin checked bank statement\""));
        verify(paymentTransactionRepository).save(transaction);
        verify(subscriptionService).activatePaidPlan("user-1", builderPlan);
    }

    @Test
    void reconcilePaymentUsesCurrentTimeWhenPaidAtIsMissingAndDropsBlankOptionalFields() {
        UUID paymentId = UUID.randomUUID();
        PaymentTransaction transaction = payment(PaymentStatus.PENDING);
        ReconcilePaymentRequest request = reconcileRequest("BANK-9001");
        request.setProviderReferenceCode("   ");
        request.setNote(null);
        when(paymentTransactionRepository.findWithLockByPaymentId(paymentId)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.findByProviderTransactionId("BANK-9001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(transaction)).thenReturn(transaction);
        when(paymentMapper.toResponse(transaction)).thenReturn(PaymentTransactionResponse.builder().build());

        adminPaymentService.reconcilePayment(paymentId, request);

        assertNotNull(transaction.getPaidAt());
        assertEquals(null, transaction.getProviderReferenceCode());
        assertFalse(transaction.getRawCallbackData().contains("providerReferenceCode"));
        assertFalse(transaction.getRawCallbackData().contains("note"));
    }

    private ReconcilePaymentRequest reconcileRequest(String providerTransactionId) {
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setProviderTransactionId(providerTransactionId);
        return request;
    }

    private PaymentTransaction payment(PaymentStatus status) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPaymentId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setPlan(builderPlan);
        transaction.setProvider(PaymentProvider.SEPAY);
        transaction.setStatus(status);
        transaction.setTxnRef("DH123");
        transaction.setAmount(new BigDecimal("100000"));
        transaction.setCurrency("VND");
        transaction.setSubscriptionMonths(1);
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
