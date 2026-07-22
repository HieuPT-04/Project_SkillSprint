package com.skillsprint.service.subscription;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.subscription.QuotaStatusResponse;
import com.skillsprint.entity.Feature;
import com.skillsprint.entity.PlanFeature;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CalendarScheduleRunRepository;
import com.skillsprint.repository.CommunityRoomRepository;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.PlanFeatureRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    SubscriptionService subscriptionService;

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    UploadedMaterialRepository uploadedMaterialRepository;

    @Mock
    LearningStructureVersionRepository learningStructureVersionRepository;

    @Mock
    RoadmapRepository roadmapRepository;

    @Mock
    CalendarScheduleRunRepository calendarScheduleRunRepository;

    @Mock
    PlanFeatureRepository planFeatureRepository;

    @Mock
    CommunityRoomRepository communityRoomRepository;

    QuotaService quotaService;
    ServicePlan freePlan;
    ServicePlan builderPlan;
    ServicePlan premiumPlan;

    @BeforeEach
    void setUp() {
        quotaService = new QuotaService(
                subscriptionService,
                workspaceRepository,
                uploadedMaterialRepository,
                learningStructureVersionRepository,
                roadmapRepository,
                calendarScheduleRunRepository,
                planFeatureRepository,
                communityRoomRepository
        );
        freePlan = plan(ServicePlanType.FREE, 1, 5, 0, 5, 20, 100);
        builderPlan = plan(ServicePlanType.SKILL_BUILDER, 3, 20, 2, 25, 50, 500);
        premiumPlan = plan(ServicePlanType.PREMIUM, 10, 100, 10, 100, 200, 2048);
    }

    @Test
    void getQuotaStatusCalculatesUsageAndRemainingValues() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(builderPlan);
        when(workspaceRepository.countByUserUserIdAndStatusNot("user-1", WorkspaceStatus.DELETED)).thenReturn(2L);
        when(uploadedMaterialRepository.countByUserUserId("user-1")).thenReturn(7L);
        when(communityRoomRepository.countByOwnerUserIdAndStatusNot("user-1", CommunityRoomStatus.DELETED)).thenReturn(1L);
        when(learningStructureVersionRepository.countByUserId("user-1")).thenReturn(3L);
        when(roadmapRepository.countByUserUserId("user-1")).thenReturn(4L);
        when(calendarScheduleRunRepository.countByUserUserId("user-1")).thenReturn(5L);
        when(uploadedMaterialRepository.sumFileSizeByUserId("user-1")).thenReturn(1572864L);

        QuotaStatusResponse response = quotaService.getQuotaStatus("user-1");

        assertEquals(ServicePlanType.SKILL_BUILDER, response.getPlan());
        assertEquals(3, response.getMaxWorkspaces());
        assertEquals(2L, response.getUsedWorkspaces());
        assertEquals(1L, response.getRemainingWorkspaces());
        assertEquals(20, response.getMaxUploads());
        assertEquals(7L, response.getUsedUploads());
        assertEquals(13L, response.getRemainingUploads());
        assertEquals(2, response.getMaxCommunityRooms());
        assertEquals(1L, response.getUsedCommunityRooms());
        assertEquals(1L, response.getRemainingCommunityRooms());
        assertEquals(25, response.getAiGenerateLimit());
        assertEquals(12L, response.getUsedAiGenerate());
        assertEquals(13L, response.getRemainingAiGenerate());
        assertEquals(50, response.getMaxFileMb());
        assertEquals(500, response.getMaxWorkspaceMb());
        assertEquals(1572864L, response.getUsedStorageBytes());
        assertEquals(1.5, response.getUsedStorageMb());
        assertEquals(498.5, response.getRemainingStorageMb());
    }

    @Test
    void validateCanCreateWorkspaceRejectsWhenLimitReached() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(workspaceRepository.countByUserUserIdAndStatusNot("user-1", WorkspaceStatus.DELETED)).thenReturn(1L);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanCreateWorkspace("user-1")
        );

        assertEquals(ErrorCode.QUOTA_WORKSPACE_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void validateCanStartMaterialUploadRejectsWhenLimitReached() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(uploadedMaterialRepository.countByUserUserId("user-1")).thenReturn(5L);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanStartMaterialUpload("user-1")
        );

        assertEquals(ErrorCode.QUOTA_UPLOAD_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void validateCanConfirmMaterialUploadRejectsFileOverPlanLimit() {
        UUID workspaceId = UUID.randomUUID();
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanConfirmMaterialUpload(
                        "user-1",
                        workspaceId,
                        21L * 1024 * 1024
                )
        );

        assertEquals(ErrorCode.QUOTA_FILE_SIZE_LIMIT_EXCEEDED, exception.getErrorCode());
        assertEquals("File vượt quá giới hạn 20MB của gói hiện tại", exception.getMessage());
        verify(uploadedMaterialRepository, never()).sumFileSizeByWorkspaceIdAndUserId(workspaceId, "user-1");
    }

    @Test
    void validateCanConfirmMaterialUploadRejectsWorkspaceStorageOverflow() {
        UUID workspaceId = UUID.randomUUID();
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(uploadedMaterialRepository.sumFileSizeByWorkspaceIdAndUserId(workspaceId, "user-1"))
                .thenReturn(95L * 1024 * 1024);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanConfirmMaterialUpload(
                        "user-1",
                        workspaceId,
                        10L * 1024 * 1024
                )
        );

        assertEquals(ErrorCode.QUOTA_STORAGE_LIMIT_EXCEEDED, exception.getErrorCode());
        assertEquals("Workspace vượt quá giới hạn 100MB của gói hiện tại", exception.getMessage());
    }

    @Test
    void validateCanConfirmMaterialUploadAllowsWithinFileAndWorkspaceLimits() {
        UUID workspaceId = UUID.randomUUID();
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(uploadedMaterialRepository.sumFileSizeByWorkspaceIdAndUserId(workspaceId, "user-1"))
                .thenReturn(90L * 1024 * 1024);

        assertDoesNotThrow(() -> quotaService.validateCanConfirmMaterialUpload(
                "user-1",
                workspaceId,
                10L * 1024 * 1024
        ));
    }

    @Test
    void validateCanGenerateAiRejectsWhenCombinedAiUsageReachesLimit() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(learningStructureVersionRepository.countByUserId("user-1")).thenReturn(2L);
        when(roadmapRepository.countByUserUserId("user-1")).thenReturn(2L);
        when(calendarScheduleRunRepository.countByUserUserId("user-1")).thenReturn(1L);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanGenerateAi("user-1")
        );

        assertEquals(ErrorCode.QUOTA_AI_GENERATE_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void validateCanCreateCommunityRoomRejectsWhenFeatureDisabled() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                freePlan.getPlanId(),
                PlanFeatureKeys.COMMUNITY_ROOM
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanCreateCommunityRoom("user-1")
        );

        assertEquals(ErrorCode.PREMIUM_FEATURE_REQUIRED, exception.getErrorCode());
        verify(communityRoomRepository, never()).countByOwnerUserIdAndStatusNot("user-1", CommunityRoomStatus.DELETED);
    }

    @Test
    void validateCanCreateCommunityRoomRejectsWhenFeatureEnabledButRoomLimitReached() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(builderPlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                builderPlan.getPlanId(),
                PlanFeatureKeys.COMMUNITY_ROOM
        )).thenReturn(Optional.of(planFeature(builderPlan, PlanFeatureKeys.COMMUNITY_ROOM, true, true)));
        when(communityRoomRepository.countByOwnerUserIdAndStatusNot("user-1", CommunityRoomStatus.DELETED))
                .thenReturn(2L);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanCreateCommunityRoom("user-1")
        );

        assertEquals(ErrorCode.QUOTA_COMMUNITY_ROOM_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void validateFeatureUsesPlanFeatureWhenConfigured() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                freePlan.getPlanId(),
                PlanFeatureKeys.AI_TUTOR
        )).thenReturn(Optional.of(planFeature(freePlan, PlanFeatureKeys.AI_TUTOR, true, true)));

        assertDoesNotThrow(() -> quotaService.validateFeature("user-1", PlanFeatureKeys.AI_TUTOR));
    }

    @Test
    void validateFeatureRejectsInactiveFeatureEvenWhenPlanFeatureEnabled() {
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(premiumPlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                premiumPlan.getPlanId(),
                PlanFeatureKeys.AI_TUTOR
        )).thenReturn(Optional.of(planFeature(premiumPlan, PlanFeatureKeys.AI_TUTOR, true, false)));

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateFeature("user-1", PlanFeatureKeys.AI_TUTOR)
        );

        assertEquals(ErrorCode.PREMIUM_FEATURE_REQUIRED, exception.getErrorCode());
    }

    @Test
    void validateFeatureBlocksFreeLearningStructureRegenerationWhenFeatureIsNotSeeded() {
        when(subscriptionService.getCurrentPlan("free-user")).thenReturn(freePlan);
        when(subscriptionService.getCurrentPlan("paid-user")).thenReturn(builderPlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                freePlan.getPlanId(),
                PlanFeatureKeys.LEARNING_STRUCTURE_REGENERATION
        )).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                builderPlan.getPlanId(),
                PlanFeatureKeys.LEARNING_STRUCTURE_REGENERATION
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateFeature("free-user", PlanFeatureKeys.LEARNING_STRUCTURE_REGENERATION)
        );

        assertEquals(ErrorCode.PREMIUM_FEATURE_REQUIRED, exception.getErrorCode());
        assertDoesNotThrow(() -> quotaService.validateFeature(
                "paid-user",
                PlanFeatureKeys.LEARNING_STRUCTURE_REGENERATION
        ));
    }

    @Test
    void getUnlockedRoadmapStepLimitFallsBackToLegacyRulesWhenFeatureIsMissing() {
        when(subscriptionService.getCurrentPlan("free-user")).thenReturn(freePlan);
        when(subscriptionService.getCurrentPlan("paid-user")).thenReturn(builderPlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                freePlan.getPlanId(),
                PlanFeatureKeys.ROADMAP_FULL_ACCESS
        )).thenReturn(Optional.empty());
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                builderPlan.getPlanId(),
                PlanFeatureKeys.ROADMAP_FULL_ACCESS
        )).thenReturn(Optional.empty());

        assertEquals(2, quotaService.getUnlockedRoadmapStepLimit("free-user"));
        assertEquals(Integer.MAX_VALUE, quotaService.getUnlockedRoadmapStepLimit("paid-user"));
    }

    @Test
    void validateCanAccessRoadmapStepRejectsLockedFreeStep() {
        RoadmapStep step = new RoadmapStep();
        step.setSequenceNo(3);
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(freePlan);
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                freePlan.getPlanId(),
                PlanFeatureKeys.ROADMAP_FULL_ACCESS
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanAccessRoadmapStep("user-1", step)
        );

        assertEquals(ErrorCode.QUOTA_ROADMAP_STEP_LOCKED, exception.getErrorCode());
    }

    @Test
    void validateCanAccessRoadmapStepIgnoresNullOrUnsequencedStep() {
        RoadmapStep step = new RoadmapStep();

        assertDoesNotThrow(() -> quotaService.validateCanAccessRoadmapStep("user-1", null));
        assertDoesNotThrow(() -> quotaService.validateCanAccessRoadmapStep("user-1", step));
        verify(subscriptionService, never()).getCurrentPlan("user-1");
    }

    private ServicePlan plan(
            ServicePlanType planType,
            Integer maxWorkspaces,
            Integer maxUploads,
            Integer maxCommunityRooms,
            Integer aiGenerateLimit,
            Integer maxFileMb,
            Integer maxWorkspaceMb
    ) {
        ServicePlan plan = new ServicePlan();
        plan.setPlanId(UUID.randomUUID());
        plan.setPlanType(planType);
        plan.setPlanName(planType.name());
        plan.setMonthlyPrice(java.math.BigDecimal.ZERO);
        plan.setMaxWorkspaces(maxWorkspaces);
        plan.setMaxUploads(maxUploads);
        plan.setMaxCommunityRooms(maxCommunityRooms);
        plan.setAiParsingLimit(aiGenerateLimit);
        plan.setMaxFileMb(maxFileMb);
        plan.setMaxWorkspaceMb(maxWorkspaceMb);
        return plan;
    }

    private PlanFeature planFeature(
            ServicePlan plan,
            String featureKey,
            boolean enabled,
            boolean active
    ) {
        Feature feature = new Feature();
        feature.setFeatureId(UUID.randomUUID());
        feature.setFeatureKey(featureKey);
        feature.setFeatureName(featureKey);
        feature.setActive(active);

        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlanFeatureId(UUID.randomUUID());
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setEnabled(enabled);
        return planFeature;
    }
}
