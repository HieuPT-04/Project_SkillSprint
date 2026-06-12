package com.skillsprint.mapper;

import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.ServicePlanFeatureResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
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
                .planType(plan.getPlanType())
                .monthlyPrice(plan.getMonthlyPrice())
                .currency(defaultString(plan.getCurrency(), "VND"))
                .maxWorkspaces(defaultValue(plan.getMaxWorkspaces(), 1))
                .maxUploads(defaultValue(plan.getMaxUploads(), 5))
                .aiGenerateLimit(defaultValue(plan.getAiParsingLimit(), 5))
                .maxFileMb(defaultValue(plan.getMaxFileMb(), 20))
                .maxWorkspaceMb(defaultValue(plan.getMaxWorkspaceMb(), 100))
                .active(plan.isActive())
                .publicVisible(defaultBoolean(plan.getPublicVisible(), true))
                .sortOrder(defaultValue(plan.getSortOrder(), 0))
                .features(features.stream().map(this::toFeatureResponse).toList())
                .build();
    }

    public CurrentSubscriptionResponse toCurrentSubscriptionResponse(Subscription subscription) {
        return toCurrentSubscriptionResponse(subscription, List.of());
    }

    public CurrentSubscriptionResponse toCurrentSubscriptionResponse(Subscription subscription, List<PlanFeature> features) {
        return CurrentSubscriptionResponse.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .plan(toServicePlanResponse(subscription.getPlan(), features))
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
