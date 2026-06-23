package com.skillsprint.service.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.UserServicePlanResponse;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.Subscription;
import com.skillsprint.entity.User;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.SubscriptionMapper;
import com.skillsprint.repository.PlanFeatureRepository;
import com.skillsprint.repository.ServicePlanRepository;
import com.skillsprint.repository.SubscriptionRepository;
import com.skillsprint.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    ServicePlanRepository servicePlanRepository;

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PlanFeatureRepository planFeatureRepository;

    @Mock
    SubscriptionMapper subscriptionMapper;

    SubscriptionService subscriptionService;
    User user;
    ServicePlan freePlan;
    ServicePlan builderPlan;
    ServicePlan premiumPlan;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                servicePlanRepository,
                subscriptionRepository,
                userRepository,
                planFeatureRepository,
                subscriptionMapper
        );
        user = user("user-1");
        freePlan = plan(ServicePlanType.FREE, "Free", "0", 0);
        builderPlan = plan(ServicePlanType.SKILL_BUILDER, "Skill Builder", "100000", 1);
        premiumPlan = plan(ServicePlanType.PREMIUM, "Premium", "200000", 2);
    }

    @Test
    void ensureDefaultFreeSubscriptionRejectsMissingUser() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> subscriptionService.ensureDefaultFreeSubscription("missing")
        );

        assertEquals(ErrorCode.USER_PROFILE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void ensureDefaultFreeSubscriptionCreatesFreePlanWhenNoActiveSubscriptionExists() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(servicePlanRepository.findByPlanType(ServicePlanType.FREE)).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription subscription = subscriptionService.ensureDefaultFreeSubscription("user-1");

        assertSame(user, subscription.getUser());
        assertSame(freePlan, subscription.getPlan());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertNotNull(subscription.getStartAt());
        assertEquals(null, subscription.getEndAt());
    }

    @Test
    void ensureDefaultFreeSubscriptionReturnsCurrentActiveSubscription() {
        Subscription active = subscription(user, builderPlan, Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(active));

        assertSame(active, subscriptionService.ensureDefaultFreeSubscription(user));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void ensureDefaultFreeSubscriptionNeverExpiresAdminDefaultPlan() {
        ServicePlan adminPlan = plan(ServicePlanType.ADMIN_DEFAULT, "Admin", "0", 3);
        Subscription adminSubscription = subscription(
                user,
                adminPlan,
                Instant.now().minus(Duration.ofDays(60)),
                Instant.now().minus(Duration.ofDays(30))
        );
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(adminSubscription));

        assertSame(adminSubscription, subscriptionService.ensureDefaultFreeSubscription(user));
        assertEquals(SubscriptionStatus.ACTIVE, adminSubscription.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void ensureDefaultFreeSubscriptionExpiresPaidPlanAndCreatesFreeReplacement() {
        Subscription expired = subscription(
                user,
                builderPlan,
                Instant.now().minus(Duration.ofDays(60)),
                Instant.now().minusSeconds(1)
        );
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(expired));
        when(servicePlanRepository.findByPlanType(ServicePlanType.FREE)).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Subscription replacement = subscriptionService.ensureDefaultFreeSubscription(user);

        assertEquals(SubscriptionStatus.EXPIRED, expired.getStatus());
        assertSame(freePlan, replacement.getPlan());
        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
    }

    @Test
    void ensureDefaultFreeSubscriptionNormalizesLegacyDateFields() {
        Subscription legacy = new Subscription();
        legacy.setUser(user);
        legacy.setPlan(builderPlan);
        legacy.setStatus(SubscriptionStatus.ACTIVE);
        legacy.setStartDate(LocalDate.now().minusDays(10));
        legacy.setEndDate(LocalDate.now().plusDays(10));
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(legacy));

        Subscription result = subscriptionService.ensureDefaultFreeSubscription(user);

        assertNotNull(result.getStartAt());
        assertNotNull(result.getEndAt());
    }

    @Test
    void activatePlanCancelsCurrentSubscriptionAndCreatesRequestedPlan() {
        Subscription current = subscription(user, freePlan, Instant.now().minusSeconds(60), null);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(current));
        when(servicePlanRepository.findByPlanType(ServicePlanType.SKILL_BUILDER))
                .thenReturn(Optional.of(builderPlan));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CurrentSubscriptionResponse expected = CurrentSubscriptionResponse.builder().build();
        when(planFeatureRepository.findByPlanPlanId(builderPlan.getPlanId())).thenReturn(List.of());
        when(subscriptionMapper.toCurrentSubscriptionResponse(any(Subscription.class), any())).thenReturn(expected);

        CurrentSubscriptionResponse response =
                subscriptionService.activatePlan("user-1", ServicePlanType.SKILL_BUILDER);

        assertSame(expected, response);
        assertEquals(SubscriptionStatus.CANCELED, current.getStatus());
        assertNotNull(current.getEndAt());
        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
    }

    @Test
    void activatePaidPlanRejectsDowngrade() {
        Subscription current = subscription(
                user,
                premiumPlan,
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().plus(Duration.ofDays(20))
        );
        stubCurrentSubscription(current);

        AppException exception = assertThrows(
                AppException.class,
                () -> subscriptionService.activatePaidPlan("user-1", builderPlan)
        );

        assertEquals(ErrorCode.SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED, exception.getErrorCode());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void activatePaidPlanExtendsSamePaidPlanByOneMonth() {
        Instant originalEndAt = Instant.parse("2026-07-23T08:00:00Z");
        Subscription current = subscription(
                user,
                builderPlan,
                Instant.parse("2026-06-23T08:00:00Z"),
                originalEndAt
        );
        stubCurrentSubscription(current);
        when(subscriptionRepository.save(current)).thenReturn(current);
        CurrentSubscriptionResponse expected = CurrentSubscriptionResponse.builder().build();
        stubCurrentResponse(current, expected);

        CurrentSubscriptionResponse response =
                subscriptionService.activatePaidPlan("user-1", builderPlan);

        assertSame(expected, response);
        assertEquals(
                originalEndAt.atZone(SubscriptionService.VN_ZONE).plusMonths(1).toInstant(),
                current.getEndAt()
        );
        assertEquals(SubscriptionStatus.ACTIVE, current.getStatus());
    }

    @Test
    void activatePaidPlanUpgradesPaidPlanUsingCurrentEndDate() {
        Instant currentEndAt = Instant.now().plus(Duration.ofDays(20));
        Subscription current = subscription(
                user,
                builderPlan,
                Instant.now().minus(Duration.ofDays(10)),
                currentEndAt
        );
        stubCurrentSubscription(current);
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CurrentSubscriptionResponse expected = CurrentSubscriptionResponse.builder().build();
        when(planFeatureRepository.findByPlanPlanId(premiumPlan.getPlanId())).thenReturn(List.of());
        when(subscriptionMapper.toCurrentSubscriptionResponse(any(Subscription.class), any())).thenReturn(expected);

        CurrentSubscriptionResponse response =
                subscriptionService.activatePaidPlan("user-1", premiumPlan);

        assertSame(expected, response);
        assertEquals(SubscriptionStatus.CANCELED, current.getStatus());
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        Subscription created = captor.getAllValues().get(1);
        assertSame(premiumPlan, created.getPlan());
        assertEquals(currentEndAt, created.getEndAt());
    }

    @Test
    void activatePaidPlanFromFreeCreatesOneMonthSubscription() {
        Subscription current = subscription(user, freePlan, Instant.now().minus(Duration.ofDays(1)), null);
        stubCurrentSubscription(current);
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(planFeatureRepository.findByPlanPlanId(builderPlan.getPlanId())).thenReturn(List.of());
        when(subscriptionMapper.toCurrentSubscriptionResponse(any(Subscription.class), any()))
                .thenReturn(CurrentSubscriptionResponse.builder().build());

        subscriptionService.activatePaidPlan("user-1", builderPlan);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        Subscription created = captor.getAllValues().get(1);
        long durationDays = Duration.between(created.getStartAt(), created.getEndAt()).toDays();
        assertTrue(durationDays >= 27 && durationDays <= 31);
    }

    @Test
    void calculatePaymentAmountReturnsFullRoundedPriceForFreeToPaidAndSamePlan() {
        builderPlan.setMonthlyPrice(new BigDecimal("100000.25"));
        Subscription freeSubscription = subscription(user, freePlan, Instant.now().minusSeconds(60), null);
        stubCurrentSubscription(freeSubscription);

        assertEquals(
                new BigDecimal("100001"),
                subscriptionService.calculatePaymentAmount("user-1", builderPlan)
        );

        Subscription builderSubscription = subscription(
                user,
                builderPlan,
                Instant.now().minus(Duration.ofDays(1)),
                Instant.now().plus(Duration.ofDays(29))
        );
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(builderSubscription));

        assertEquals(
                new BigDecimal("100001"),
                subscriptionService.calculatePaymentAmount("user-1", builderPlan)
        );
    }

    @Test
    void calculatePaymentAmountProratesPaidUpgradeByRemainingTime() {
        Instant now = Instant.now();
        Subscription current = subscription(
                user,
                builderPlan,
                now.minus(Duration.ofDays(15)),
                now.plus(Duration.ofDays(15))
        );
        stubCurrentSubscription(current);

        BigDecimal amount = subscriptionService.calculatePaymentAmount("user-1", premiumPlan);

        assertTrue(amount.compareTo(new BigDecimal("49990")) >= 0);
        assertTrue(amount.compareTo(new BigDecimal("50010")) <= 0);
    }

    @Test
    void calculatePaymentAmountRejectsDowngrade() {
        Subscription current = subscription(
                user,
                premiumPlan,
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().plus(Duration.ofDays(20))
        );
        stubCurrentSubscription(current);

        AppException exception = assertThrows(
                AppException.class,
                () -> subscriptionService.calculatePaymentAmount("user-1", builderPlan)
        );

        assertEquals(ErrorCode.SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    void expirePaidSubscriptionsExpiresPlansAndCreatesFreeSubscriptions() {
        Subscription expired = subscription(
                user,
                builderPlan,
                Instant.now().minus(Duration.ofDays(30)),
                Instant.now().minusSeconds(1)
        );
        when(subscriptionRepository.findByStatusAndEndAtBefore(
                any(SubscriptionStatus.class),
                any(Instant.class)
        )).thenReturn(List.of(expired));
        when(servicePlanRepository.findByPlanType(ServicePlanType.FREE)).thenReturn(Optional.of(freePlan));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionService.expirePaidSubscriptions();

        assertEquals(SubscriptionStatus.EXPIRED, expired.getStatus());
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        assertSame(freePlan, captor.getAllValues().get(1).getPlan());
    }

    @Test
    void getActivePlansMapsVisiblePlansWithTheirFeatures() {
        UserServicePlanResponse mapped = UserServicePlanResponse.builder().build();
        when(servicePlanRepository.findVisibleActivePlans()).thenReturn(List.of(builderPlan));
        when(planFeatureRepository.findByPlanPlanId(builderPlan.getPlanId())).thenReturn(List.of());
        when(subscriptionMapper.toUserServicePlanResponse(builderPlan, List.of())).thenReturn(mapped);

        assertEquals(List.of(mapped), subscriptionService.getActivePlans());
        verify(subscriptionMapper, atLeastOnce()).toUserServicePlanResponse(builderPlan, List.of());
    }

    private void stubCurrentSubscription(Subscription current) {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findTopByUserUserIdAndStatusOrderByCreatedAtDesc(
                "user-1",
                SubscriptionStatus.ACTIVE
        )).thenReturn(Optional.of(current));
    }

    private void stubCurrentResponse(
            Subscription subscription,
            CurrentSubscriptionResponse response
    ) {
        when(planFeatureRepository.findByPlanPlanId(subscription.getPlan().getPlanId())).thenReturn(List.of());
        when(subscriptionMapper.toCurrentSubscriptionResponse(subscription, List.of())).thenReturn(response);
    }

    private User user(String userId) {
        User value = new User();
        value.setUserId(userId);
        value.setEmail(userId + "@example.com");
        value.setFullName("Test User");
        return value;
    }

    private ServicePlan plan(
            ServicePlanType type,
            String name,
            String price,
            int sortOrder
    ) {
        ServicePlan value = new ServicePlan();
        value.setPlanId(UUID.randomUUID());
        value.setPlanType(type);
        value.setPlanName(name);
        value.setMonthlyPrice(new BigDecimal(price));
        value.setSortOrder(sortOrder);
        value.setActive(true);
        value.setPublicVisible(true);
        return value;
    }

    private Subscription subscription(
            User owner,
            ServicePlan plan,
            Instant startAt,
            Instant endAt
    ) {
        Subscription value = new Subscription();
        value.setSubscriptionId(UUID.randomUUID());
        value.setUser(owner);
        value.setPlan(plan);
        value.setStartAt(startAt);
        value.setEndAt(endAt);
        value.setStartDate(startAt.atZone(SubscriptionService.VN_ZONE).toLocalDate());
        value.setEndDate(endAt == null ? null : endAt.atZone(SubscriptionService.VN_ZONE).toLocalDate());
        value.setStatus(SubscriptionStatus.ACTIVE);
        return value;
    }
}
