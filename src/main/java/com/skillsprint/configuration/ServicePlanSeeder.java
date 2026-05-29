package com.skillsprint.configuration;

import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.repository.ServicePlanRepository;
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsertPlan(
                ServicePlanType.FREE,
                "Free",
                BigDecimal.ZERO,
                1,
                5,
                5,
                20,
                100
        );

        upsertPlan(
                ServicePlanType.SKILL_BUILDER,
                "Basic",
                new BigDecimal("99000"),
                5,
                50,
                50,
                50,
                1000
        );

        upsertPlan(
                ServicePlanType.PREMIUM,
                "Premium",
                new BigDecimal("199000"),
                20,
                300,
                300,
                100,
                10000
        );
    }

    private void upsertPlan(
            ServicePlanType planType,
            String planName,
            BigDecimal monthlyPrice,
            int maxWorkspaces,
            int maxUploads,
            int aiGenerateLimit,
            int maxFileMb,
            int maxWorkspaceMb
    ) {
        ServicePlan plan = servicePlanRepository.findByPlanType(planType)
                .orElseGet(ServicePlan::new);

        plan.setPlanType(planType);
        plan.setPlanName(planName);
        plan.setMonthlyPrice(monthlyPrice);
        plan.setMaxWorkspaces(maxWorkspaces);
        plan.setMaxUploads(maxUploads);
        plan.setAiParsingLimit(aiGenerateLimit);
        plan.setMaxFileMb(maxFileMb);
        plan.setMaxWorkspaceMb(maxWorkspaceMb);
        plan.setActive(true);

        servicePlanRepository.save(plan);
        log.info("Seeded/updated service plan {}", planType);
    }
}