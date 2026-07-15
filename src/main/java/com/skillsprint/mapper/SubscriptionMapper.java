package com.skillsprint.mapper;

import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.ServicePlanFeatureResponse;
import com.skillsprint.dto.response.subscription.ServicePlanQuotaResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
import com.skillsprint.dto.response.subscription.UserServicePlanResponse;
import com.skillsprint.entity.PlanFeature;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.Subscription;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public ServicePlanResponse toServicePlanResponse(ServicePlan plan) {
        return toServicePlanResponse(plan, List.of());
    }

    public ServicePlanResponse toServicePlanResponse(ServicePlan plan, List<PlanFeature> features) {
        return ServicePlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .description(plan.getDescription())
                .benefits(defaultList(plan.getBenefits()))
                .planType(plan.getPlanType())
                .badgeColor(plan.getBadgeColor())
                .badgeIcon(plan.getBadgeIcon())
                .animationType(plan.getAnimationType())
                .monthlyPrice(plan.getMonthlyPrice())
                .currency(defaultString(plan.getCurrency(), "VND"))
                .quotas(toQuotaResponse(plan))
                .active(plan.isActive())
                .publicVisible(defaultBoolean(plan.getPublicVisible(), true))
                .sortOrder(defaultValue(plan.getSortOrder(), 0))
                .features(features.stream().map(this::toFeatureResponse).toList())
                .build();
    }

    public UserServicePlanResponse toUserServicePlanResponse(ServicePlan plan, List<PlanFeature> features) {
        return UserServicePlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .planType(plan.getPlanType())
                .description(plan.getDescription())
                .benefits(defaultList(plan.getBenefits()))
                .badgeColor(plan.getBadgeColor())
                .badgeIcon(plan.getBadgeIcon())
                .animationType(plan.getAnimationType())
                .monthlyPrice(plan.getMonthlyPrice())
                .currency(defaultString(plan.getCurrency(), "VND"))
                .quotas(toQuotaResponse(plan))
                .build();
    }

    public CurrentSubscriptionResponse toCurrentSubscriptionResponse(Subscription subscription) {
        return toCurrentSubscriptionResponse(subscription, List.of());
    }

    public CurrentSubscriptionResponse toCurrentSubscriptionResponse(Subscription subscription, List<PlanFeature> features) {
        return CurrentSubscriptionResponse.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .plan(toUserServicePlanResponse(subscription.getPlan(), features))
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .startAt(subscription.getStartAt())
                .endAt(subscription.getEndAt())
                .status(subscription.getStatus())
                .createdAt(subscription.getCreatedAt())
                .build();
    }

    private Integer defaultValue(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private Boolean defaultBoolean(Boolean value, Boolean fallback) {
        return value == null ? fallback : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private ServicePlanQuotaResponse toQuotaResponse(ServicePlan plan) {
        return ServicePlanQuotaResponse.builder()
                .maxWorkspaces(defaultValue(plan.getMaxWorkspaces(), 1))
                .maxUploads(defaultValue(plan.getMaxUploads(), 5))
                .maxCommunityRooms(defaultValue(plan.getMaxCommunityRooms(), 0))
                .aiGenerateLimit(defaultValue(plan.getAiParsingLimit(), 5))
                .maxFileMb(defaultValue(plan.getMaxFileMb(), 20))
                .maxWorkspaceMb(defaultValue(plan.getMaxWorkspaceMb(), 100))
                .build();
    }

    private ServicePlanFeatureResponse toFeatureResponse(PlanFeature planFeature) {
        return ServicePlanFeatureResponse.builder()
                .featureId(planFeature.getFeature().getFeatureId())
                .featureKey(planFeature.getFeature().getFeatureKey())
                .featureName(planFeature.getFeature().getFeatureName())
                .description(planFeature.getFeature().getDescription())
                .active(planFeature.getFeature().isActive())
                .enabled(planFeature.isEnabled())
                .build();
    }

}
