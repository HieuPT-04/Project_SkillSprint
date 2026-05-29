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
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubscriptionService {

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
        return subscriptionRepository
                .findTopByUserUserIdAndStatusOrderByCreatedAtDesc(user.getUserId(), SubscriptionStatus.ACTIVE)
                .orElseGet(() -> createSubscription(user, ServicePlanType.FREE));
    }

    @Transactional
    public CurrentSubscriptionResponse activatePlan(String userId, ServicePlanType planType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        subscriptionRepository
                .findTopByUserUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(current -> {
                    current.setStatus(SubscriptionStatus.CANCELED);
                    current.setEndDate(LocalDate.now());
                    subscriptionRepository.save(current);
                });

        Subscription subscription = createSubscription(user, planType);
        return subscriptionMapper.toCurrentSubscriptionResponse(subscription);
    }

    @Transactional
    public ServicePlan getCurrentPlan(String userId) {
        return ensureDefaultFreeSubscription(userId).getPlan();
    }

    private Subscription createSubscription(User user, ServicePlanType planType) {
        return createSubscription(user, planType, null);
    }

    private Subscription createSubscription(User user, ServicePlanType planType, LocalDate endDate) {
        ServicePlan plan = servicePlanRepository.findByPlanType(planType)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_PLAN_NOT_FOUND));

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(endDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public CurrentSubscriptionResponse activatePaidPlan(String userId, ServicePlanType planType, int months) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        subscriptionRepository
                .findTopByUserUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(current -> {
                    current.setStatus(SubscriptionStatus.CANCELED);
                    current.setEndDate(LocalDate.now());
                    subscriptionRepository.save(current);
                });

        LocalDate endDate = LocalDate.now().plusMonths(Math.max(1, months));
        Subscription subscription = createSubscription(user, planType, endDate);

        return subscriptionMapper.toCurrentSubscriptionResponse(subscription);
    }
}