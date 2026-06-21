package com.skillsprint.service.subscription;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.Feature;
import com.skillsprint.entity.PlanFeature;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.community.CommunityRoomStatus;
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

    @Mock SubscriptionService subscriptionService;
    @Mock StudyWorkspaceRepository workspaceRepository;
    @Mock UploadedMaterialRepository uploadedMaterialRepository;
    @Mock LearningStructureVersionRepository learningStructureVersionRepository;
    @Mock RoadmapRepository roadmapRepository;
    @Mock CalendarScheduleRunRepository calendarScheduleRunRepository;
    @Mock PlanFeatureRepository planFeatureRepository;
    @Mock CommunityRoomRepository communityRoomRepository;

    QuotaService quotaService;
    ServicePlan plan;

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
        plan = new ServicePlan();
        plan.setPlanId(UUID.randomUUID());
        plan.setMaxCommunityRooms(3);
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(plan);
    }

    @Test
    void validateFeatureRejectsDisabledCommunityRoom() {
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                plan.getPlanId(),
                PlanFeatureKeys.COMMUNITY_ROOM
        )).thenReturn(Optional.of(planFeature(false)));

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateFeature("user-1", PlanFeatureKeys.COMMUNITY_ROOM)
        );

        assertEquals(ErrorCode.PREMIUM_FEATURE_REQUIRED, exception.getErrorCode());
    }

    @Test
    void validateCanCreateCommunityRoomAllowsUsageBelowLimit() {
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                plan.getPlanId(),
                PlanFeatureKeys.COMMUNITY_ROOM
        )).thenReturn(Optional.of(planFeature(true)));
        when(communityRoomRepository.countByOwnerUserIdAndStatusNot(
                "user-1",
                CommunityRoomStatus.DELETED
        )).thenReturn(2L);

        assertDoesNotThrow(() -> quotaService.validateCanCreateCommunityRoom("user-1"));
    }

    @Test
    void validateCanCreateCommunityRoomRejectsUsageAtLimit() {
        when(planFeatureRepository.findByPlanPlanIdAndFeatureFeatureKey(
                plan.getPlanId(),
                PlanFeatureKeys.COMMUNITY_ROOM
        )).thenReturn(Optional.of(planFeature(true)));
        when(communityRoomRepository.countByOwnerUserIdAndStatusNot(
                "user-1",
                CommunityRoomStatus.DELETED
        )).thenReturn(3L);

        AppException exception = assertThrows(
                AppException.class,
                () -> quotaService.validateCanCreateCommunityRoom("user-1")
        );

        assertEquals(ErrorCode.QUOTA_COMMUNITY_ROOM_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    private PlanFeature planFeature(boolean enabled) {
        Feature feature = new Feature();
        feature.setFeatureKey(PlanFeatureKeys.COMMUNITY_ROOM);
        feature.setActive(true);

        PlanFeature planFeature = new PlanFeature();
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setEnabled(enabled);
        return planFeature;
    }
}
