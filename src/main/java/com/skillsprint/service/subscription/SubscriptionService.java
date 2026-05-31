package com.skillsprint.service.subscription;

import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.Subscription;
import com.skillsprint.entity.User;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.SubscriptionMapper;
import com.skillsprint.repository.ServicePlanRepository;
import com.skillsprint.repository.SubscriptionRepository;
import com.skillsprint.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
public class SubscriptionService {

    static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    ServicePlanRepository servicePlanRepository;
    SubscriptionRepository subscriptionRepository;
    UserRepository userRepository;
    SubscriptionMapper subscriptionMapper;

    @Transactional(readOnly = true)
    public List<ServicePlanResponse> getActivePlans() {
        return servicePlanRepository.findByActiveTrueOrderByMonthlyPriceAsc()
                .stream()
                .map(subscriptionMapper::toServicePlanResponse)
                .toList();
    }

    @Transactional
    public CurrentSubscriptionResponse getCurrentSubscription(String userId) {
        Subscription subscription = ensureDefaultFreeSubscription(userId);
        return subscriptionMapper.toCurrentSubscriptionResponse(subscription);
    }

    @Transactional
    public Subscription ensureDefaultFreeSubscription(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        return ensureDefaultFreeSubscription(user);
    }

    @Transactional
    public Subscription ensureDefaultFreeSubscription(User user) {
        Subscription activeSubscription = subscriptionRepository
                .findTopByUserUserIdAndStatusOrderByCreatedAtDesc(user.getUserId(), SubscriptionStatus.ACTIVE)
                .orElse(null);

        if (activeSubscription == null) {
            return createFreeSubscription(user);
        }

        normalizeTimestamps(activeSubscription);

        if (isExpired(activeSubscription)) {
            activeSubscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(activeSubscription);
            return createFreeSubscription(user);
        }

        return activeSubscription;
    }

    @Transactional
    public CurrentSubscriptionResponse activatePlan(String userId, ServicePlanType planType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        subscriptionRepository
                .findTopByUserUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(current -> {
                    current.setStatus(SubscriptionStatus.CANCELED);
                    Instant now = Instant.now();
                    current.setEndAt(now);
                    current.setEndDate(now.atZone(VN_ZONE).toLocalDate());
                    subscriptionRepository.save(current);
                });

        Subscription subscription = ServicePlanType.FREE.equals(planType)
                ? createFreeSubscription(user)
                : createOneMonthSubscription(user, planType, Instant.now());
        return subscriptionMapper.toCurrentSubscriptionResponse(subscription);
    }

    @Transactional
    public ServicePlan getCurrentPlan(String userId) {
        return ensureDefaultFreeSubscription(userId).getPlan();
    }

    private Subscription createSubscription(User user, ServicePlanType planType, Instant startAt, Instant endAt) {
        ServicePlan plan = servicePlanRepository.findByPlanType(planType)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));

        Instant resolvedStartAt = startAt == null ? Instant.now() : startAt;

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStartAt(resolvedStartAt);
        subscription.setEndAt(endAt);
        subscription.setStartDate(resolvedStartAt.atZone(VN_ZONE).toLocalDate());
        subscription.setEndDate(endAt == null ? null : endAt.atZone(VN_ZONE).toLocalDate());
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public CurrentSubscriptionResponse activatePaidPlan(String userId, ServicePlanType planType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        Subscription currentSubscription = ensureDefaultFreeSubscription(user);
        ServicePlan currentPlan = currentSubscription.getPlan();

        if (planRank(planType) < planRank(currentPlan.getPlanType())) {
            throw new AppException(ErrorCode.SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED);
        }

        if (planType.equals(currentPlan.getPlanType()) && currentSubscription.getEndAt() != null) {
            Instant extendedEndAt = currentSubscription.getEndAt().atZone(VN_ZONE)
                    .plusMonths(1)
                    .toInstant();
            currentSubscription.setEndAt(extendedEndAt);
            currentSubscription.setEndDate(extendedEndAt.atZone(VN_ZONE).toLocalDate());
            return subscriptionMapper.toCurrentSubscriptionResponse(subscriptionRepository.save(currentSubscription));
        }

        Instant startAt = Instant.now();
        Instant endAt = isUpgradeFromPaidPlan(currentSubscription, planType)
                ? currentSubscription.getEndAt()
                : startAt.atZone(VN_ZONE).plusMonths(1).toInstant();

        currentSubscription.setStatus(SubscriptionStatus.CANCELED);
        currentSubscription.setEndAt(startAt);
        currentSubscription.setEndDate(startAt.atZone(VN_ZONE).toLocalDate());
        subscriptionRepository.save(currentSubscription);

        Subscription subscription = createSubscription(user, planType, startAt, endAt);

        return subscriptionMapper.toCurrentSubscriptionResponse(subscription);
    }

    @Transactional
    public BigDecimal calculatePaymentAmount(String userId, ServicePlan targetPlan) {
        Subscription currentSubscription = ensureDefaultFreeSubscription(userId);
        ServicePlan currentPlan = currentSubscription.getPlan();
        ServicePlanType targetPlanType = targetPlan.getPlanType();

        if (planRank(targetPlanType) < planRank(currentPlan.getPlanType())) {
            throw new AppException(ErrorCode.SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED);
        }

        if (targetPlanType.equals(currentPlan.getPlanType())) {
            return roundVnd(targetPlan.getMonthlyPrice());
        }

        if (!isUpgradeFromPaidPlan(currentSubscription, targetPlanType)) {
            return roundVnd(targetPlan.getMonthlyPrice());
        }

        BigDecimal priceDifference = targetPlan.getMonthlyPrice().subtract(currentPlan.getMonthlyPrice());
        if (priceDifference.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED);
        }

        long remainingSeconds = Math.max(0, Duration.between(Instant.now(), currentSubscription.getEndAt()).getSeconds());
        long totalSeconds = Math.max(1, Duration.between(currentSubscription.getStartAt(), currentSubscription.getEndAt()).getSeconds());
        BigDecimal remainingRatio = BigDecimal.valueOf(remainingSeconds)
                .divide(BigDecimal.valueOf(totalSeconds), 8, RoundingMode.HALF_UP);

        return roundVnd(priceDifference.multiply(remainingRatio));
    }

    @Scheduled(fixedDelayString = "${app.subscription.expire-fixed-delay-ms:60000}")
    @Transactional
    public void expirePaidSubscriptions() {
        List<Subscription> expiredSubscriptions = subscriptionRepository.findByStatusAndEndAtBefore(
                SubscriptionStatus.ACTIVE,
                Instant.now()
        );

        expiredSubscriptions.stream()
                .filter(this::hasExpirableEndAt)
                .forEach(subscription -> {
                    subscription.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(subscription);
                    createFreeSubscription(subscription.getUser());
                });

        if (!expiredSubscriptions.isEmpty()) {
            log.info("[SUBSCRIPTION] Expired {} subscriptions", expiredSubscriptions.size());
        }
    }

    private Subscription createFreeSubscription(User user) {
        return createSubscription(user, ServicePlanType.FREE, Instant.now(), null);
    }

    private Subscription createOneMonthSubscription(User user, ServicePlanType planType, Instant startAt) {
        Instant endAt = startAt.atZone(VN_ZONE)
                .plusMonths(1)
                .toInstant();
        return createSubscription(user, planType, startAt, endAt);
    }

    private boolean isUpgradeFromPaidPlan(Subscription currentSubscription, ServicePlanType targetPlanType) {
        return !ServicePlanType.FREE.equals(currentSubscription.getPlan().getPlanType())
                && currentSubscription.getEndAt() != null
                && planRank(targetPlanType) > planRank(currentSubscription.getPlan().getPlanType());
    }

    private int planRank(ServicePlanType planType) {
        return switch (planType) {
            case FREE -> 0;
            case SKILL_BUILDER -> 1;
            case PREMIUM -> 2;
        };
    }

    private BigDecimal roundVnd(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.CEILING);
    }

    private boolean isExpired(Subscription subscription) {
        return hasExpirableEndAt(subscription) && !subscription.getEndAt().isAfter(Instant.now());
    }

    private boolean hasExpirableEndAt(Subscription subscription) {
        normalizeTimestamps(subscription);
        return subscription.getEndAt() != null;
    }

    private void normalizeTimestamps(Subscription subscription) {
        if (subscription.getStartAt() == null) {
            Instant startAt = subscription.getCreatedAt() == null
                    ? subscription.getStartDate().atStartOfDay(VN_ZONE).toInstant()
                    : subscription.getCreatedAt();
            subscription.setStartAt(startAt);
        }

        if (subscription.getEndAt() == null && subscription.getEndDate() != null) {
            subscription.setEndAt(subscription.getEndDate()
                    .plusDays(1)
                    .atStartOfDay(VN_ZONE)
                    .toInstant());
        }
    }
}
