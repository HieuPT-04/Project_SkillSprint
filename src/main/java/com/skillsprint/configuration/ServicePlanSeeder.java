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
import java.util.List;
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
                                List.of(
                                                "Tạo 1 workspace học tập",
                                                "Upload tối đa 5 tài liệu",
                                                "Sinh roadmap cơ bản để trải nghiệm",
                                                "Phù hợp để bắt đầu với SkillSprint"),
                                BigDecimal.ZERO,
                                1,
                                5,
                                5,
                                20,
                                100,
                                0,
                                1);

                ServicePlan basic = ensurePlan(
                                ServicePlanType.SKILL_BUILDER,
                                "Basic",
                                "Gói học cá nhân với quota cao hơn",
                                List.of(
                                                "Mở khóa toàn bộ roadmap",
                                                "Tạo tối đa 5 workspace",
                                                "Upload tối đa 50 tài liệu",
                                                "Giới hạn AI generate cao hơn",
                                                "Phù hợp cho học cá nhân nghiêm túc"),
                                new BigDecimal("89000"),
                                5,
                                50,
                                50,
                                50,
                                1000,
                                3,
                                2);

                ServicePlan premium = ensurePlan(
                                ServicePlanType.PREMIUM,
                                "Premium",
                                "Gói đầy đủ cho trải nghiệm học nâng cao",
                                List.of(
                                                "Mở khóa toàn bộ roadmap",
                                                "Hỏi AI Tutor trong workspace và từng bài học",
                                                "Tạo quiz luyện tập theo roadmap step",
                                                "Quota học tập và lưu trữ cao nhất",
                                                "Phù hợp cho người học chuyên sâu"),
                                new BigDecimal("199000"),
                                20,
                                300,
                                300,
                                100,
                                10000,
                                20,
                                3);

                Feature roadmapFullAccess = ensureFeature(
                                PlanFeatureKeys.ROADMAP_FULL_ACCESS,
                                "Mở khóa toàn bộ roadmap",
                                "Cho phép học toàn bộ các step trong roadmap");
                Feature aiTutor = ensureFeature(
                                PlanFeatureKeys.AI_TUTOR,
                                "AI Tutor",
                                "Cho phép hỏi AI Tutor theo workspace hoặc roadmap step");
                Feature quizGeneration = ensureFeature(
                                PlanFeatureKeys.QUIZ_GENERATION,
                                "Quiz generation",
                                "Cho phép tạo và làm quiz từ roadmap step");
                Feature communityFeed = ensureFeature(
                                PlanFeatureKeys.COMMUNITY_FEED,
                                "Community feed",
                                "Cho phép xem, đăng bài, bình luận, like và report trong cộng đồng");
                Feature communityRoom = ensureFeature(
                                PlanFeatureKeys.COMMUNITY_ROOM,
                                "Community room",
                                "Cho phép tạo, tham gia và quản lý phòng cộng đồng");
                Feature communityChat = ensureFeature(
                                PlanFeatureKeys.COMMUNITY_CHAT,
                                "Community chat",
                                "Cho phép xem lịch sử và gửi tin nhắn trong phòng cộng đồng");
                Feature communityPin = ensureFeature(
                                PlanFeatureKeys.COMMUNITY_PIN,
                                "Community pin",
                                "Cho phép ghim thông báo, tài liệu hoặc tin nhắn quan trọng trong phòng");

                ensurePlanFeature(free, roadmapFullAccess, false);
                ensurePlanFeature(free, aiTutor, false);
                ensurePlanFeature(free, quizGeneration, false);
                ensurePlanFeature(free, communityFeed, true);
                ensurePlanFeature(free, communityRoom, false);
                ensurePlanFeature(free, communityChat, false);
                ensurePlanFeature(free, communityPin, false);

                ensurePlanFeature(basic, roadmapFullAccess, true);
                ensurePlanFeature(basic, aiTutor, false);
                ensurePlanFeature(basic, quizGeneration, false);
                ensurePlanFeature(basic, communityFeed, true);
                ensurePlanFeature(basic, communityRoom, true);
                ensurePlanFeature(basic, communityChat, true);
                ensurePlanFeature(basic, communityPin, false);

                ensurePlanFeature(premium, roadmapFullAccess, true);
                ensurePlanFeature(premium, aiTutor, true);
                ensurePlanFeature(premium, quizGeneration, true);
                ensurePlanFeature(premium, communityFeed, true);
                ensurePlanFeature(premium, communityRoom, true);
                ensurePlanFeature(premium, communityChat, true);
                ensurePlanFeature(premium, communityPin, true);

                // --- SEED ADMIN PLAN ---
                ServicePlan adminPlan = ensurePlan(
                                ServicePlanType.ADMIN_DEFAULT,
                                "Hệ Thống Admin",
                                "Gói đặc quyền tối cao dành riêng cho Ban Quản Trị",
                                List.of(
                                                "Toàn quyền truy cập hệ thống",
                                                "Vô hạn lượt tạo AI",
                                                "Bảo mật ban quản trị"),
                                BigDecimal.ZERO,
                                999, // maxWorkspaces
                                9999, // maxUploads
                                999999, // aiGenerateLimit
                                5000, // maxFileMb
                                999999, // maxWorkspaceMb
                                999999, // maxCommunityRooms
                                4 // sortOrder
                );

                // Thiết lập hiển thị công khai là false cho gói Admin
                adminPlan.setPublicVisible(false);
                servicePlanRepository.save(adminPlan);

                // Gán tất cả tính năng hiện có cho gói Admin
                ensurePlanFeature(adminPlan, roadmapFullAccess, true);
                ensurePlanFeature(adminPlan, aiTutor, true);
                ensurePlanFeature(adminPlan, quizGeneration, true);
                ensurePlanFeature(adminPlan, communityFeed, true);
                ensurePlanFeature(adminPlan, communityRoom, true);
                ensurePlanFeature(adminPlan, communityChat, true);
                ensurePlanFeature(adminPlan, communityPin, true);
        }

        private ServicePlan ensurePlan(
                        ServicePlanType planType,
                        String planName,
                        String description,
                        List<String> benefits,
                        BigDecimal monthlyPrice,
                        int maxWorkspaces,
                        int maxUploads,
                        int aiGenerateLimit,
                        int maxFileMb,
                        int maxWorkspaceMb,
                        int maxCommunityRooms,
                        int sortOrder) {
                ServicePlan existingPlan = servicePlanRepository.findByPlanType(planType).orElse(null);
                if (existingPlan != null) {
                        if (existingPlan.getMaxCommunityRooms() == null) {
                                existingPlan.setMaxCommunityRooms(maxCommunityRooms);
                                servicePlanRepository.save(existingPlan);
                                log.info("Backfilled maxCommunityRooms for existing service plan {}", planType);
                        }
                        log.info("Skipped existing service plan {}", planType);
                        return existingPlan;
                }

                ServicePlan plan = new ServicePlan();
                plan.setPlanType(planType);
                plan.setPlanName(planName);
                plan.setDescription(description);
                plan.setBenefits(benefits);
                plan.setMonthlyPrice(monthlyPrice);
                plan.setCurrency("VND");
                plan.setMaxWorkspaces(maxWorkspaces);
                plan.setMaxUploads(maxUploads);
                plan.setAiParsingLimit(aiGenerateLimit);
                plan.setMaxFileMb(maxFileMb);
                plan.setMaxWorkspaceMb(maxWorkspaceMb);
                plan.setMaxCommunityRooms(maxCommunityRooms);
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
                                feature.getFeatureKey()).isPresent()) {
                        return;
                }

                PlanFeature planFeature = new PlanFeature();
                planFeature.setPlan(plan);
                planFeature.setFeature(feature);
                planFeature.setEnabled(enabled);
                planFeatureRepository.save(planFeature);
        }
}
