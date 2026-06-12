package com.skillsprint.configuration;

import com.skillsprint.entity.Feature;
import com.skillsprint.entity.PlanFeature;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.repository.FeatureRepository;
import com.skillsprint.repository.PlanFeatureRepository;
import com.skillsprint.repository.ServicePlanRepository;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServicePlanSeeder implements ApplicationRunner {

    ServicePlanRepository servicePlanRepository;
    FeatureRepository featureRepository;
    PlanFeatureRepository planFeatureRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ServicePlan free = ensurePlan(
                ServicePlanType.FREE,
                "Free",
                "Gói miễn phí cho người mới bắt đầu",
                BigDecimal.ZERO,
                1,
                5,
                5,
                20,
                100,
                1
        );

        ServicePlan basic = ensurePlan(
                ServicePlanType.SKILL_BUILDER,
                "Basic",
                "Gói học cá nhân với quota cao hơn",
                new BigDecimal("89000"),
                5,
                50,
                50,
                50,
                1000,
                2
        );

        ServicePlan premium = ensurePlan(
                ServicePlanType.PREMIUM,
                "Premium",
                "Gói đầy đủ cho trải nghiệm học nâng cao",
                new BigDecimal("199000"),
                20,
                300,
                300,
                100,
                10000,
                3
        );

        Feature roadmapFullAccess = ensureFeature(
                PlanFeatureKeys.ROADMAP_FULL_ACCESS,
                "Mở khóa toàn bộ roadmap",
                "Cho phép học toàn bộ các step trong roadmap"
        );
        Feature aiTutor = ensureFeature(
                PlanFeatureKeys.AI_TUTOR,
                "AI Tutor",
                "Cho phép hỏi AI Tutor theo workspace hoặc roadmap step"
        );
        Feature quizGeneration = ensureFeature(
                PlanFeatureKeys.QUIZ_GENERATION,
                "Quiz generation",
                "Cho phép tạo và làm quiz từ roadmap step"
        );

        ensurePlanFeature(free, roadmapFullAccess, false);
        ensurePlanFeature(free, aiTutor, false);
        ensurePlanFeature(free, quizGeneration, false);

        ensurePlanFeature(basic, roadmapFullAccess, true);
        ensurePlanFeature(basic, aiTutor, false);
        ensurePlanFeature(basic, quizGeneration, false);

        ensurePlanFeature(premium, roadmapFullAccess, true);
        ensurePlanFeature(premium, aiTutor, true);
        ensurePlanFeature(premium, quizGeneration, true);
    }

    private ServicePlan ensurePlan(
            ServicePlanType planType,
            String planName,
            String description,
            BigDecimal monthlyPrice,
            int maxWorkspaces,
            int maxUploads,
            int aiGenerateLimit,
            int maxFileMb,
            int maxWorkspaceMb,
            int sortOrder
    ) {
        ServicePlan existingPlan = servicePlanRepository.findByPlanType(planType).orElse(null);
        if (existingPlan != null) {
            log.info("Skipped existing service plan {}", planType);
            return existingPlan;
        }

        ServicePlan plan = new ServicePlan();
        plan.setPlanType(planType);
        plan.setPlanName(planName);
        plan.setDescription(description);
        plan.setMonthlyPrice(monthlyPrice);
        plan.setCurrency("VND");
        plan.setMaxWorkspaces(maxWorkspaces);
        plan.setMaxUploads(maxUploads);
        plan.setAiParsingLimit(aiGenerateLimit);
        plan.setMaxFileMb(maxFileMb);
        plan.setMaxWorkspaceMb(maxWorkspaceMb);
        plan.setPublicVisible(true);
        plan.setSortOrder(sortOrder);
        plan.setActive(true);

        ServicePlan savedPlan = servicePlanRepository.save(plan);
        log.info("Seeded service plan {}", planType);
        return savedPlan;
    }

    private Feature ensureFeature(String featureKey, String featureName, String description) {
        return featureRepository.findByFeatureKey(featureKey)
                .orElseGet(() -> {
                    Feature feature = new Feature();
                    feature.setFeatureKey(featureKey);
                    feature.setFeatureName(featureName);
                    feature.setDescription(description);
                    feature.setActive(true);
                    Feature savedFeature = featureRepository.save(feature);
                    log.info("Seeded feature {}", featureKey);
                    return savedFeature;
                });
    }

    private void ensurePlanFeature(ServicePlan plan, Feature feature, boolean enabled) {
        if (planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                plan.getPlanId(),
                feature.getFeatureKey()
        ).isPresent()) {
            return;
        }

        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setEnabled(enabled);
        planFeatureRepository.save(planFeature);
    }
}
