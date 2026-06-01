package com.skillsprint.mapper;

import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.Subscription;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public ServicePlanResponse toServicePlanResponse(ServicePlan plan) {
        return ServicePlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .planType(plan.getPlanType())
                .monthlyPrice(plan.getMonthlyPrice())
                .maxWorkspaces(defaultValue(plan.getMaxWorkspaces(), 1))
                .maxUploads(defaultValue(plan.getMaxUploads(), 5))
                .aiGenerateLimit(defaultValue(plan.getAiParsingLimit(), 5))
                .maxFileMb(defaultValue(plan.getMaxFileMb(), 20))
                .maxWorkspaceMb(defaultValue(plan.getMaxWorkspaceMb(), 100))
                .active(plan.isActive())
                .build();
    }

    public CurrentSubscriptionResponse toCurrentSubscriptionResponse(Subscription subscription) {
        return CurrentSubscriptionResponse.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .plan(toServicePlanResponse(subscription.getPlan()))
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
}
