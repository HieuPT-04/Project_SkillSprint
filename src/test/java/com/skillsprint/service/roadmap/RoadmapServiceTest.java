package com.skillsprint.service.roadmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.entity.Chapter;
import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.Topic;
import com.skillsprint.entity.User;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.learningstructure.LearningStructureStatus;
import com.skillsprint.enums.roadmap.ResourceType;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.RoadmapMapper;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.RoadmapStepResourceRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.TopicRepository;
import com.skillsprint.service.notification.NotificationService;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.subscription.QuotaService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoadmapServiceTest {

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    LearningStructureVersionRepository structureVersionRepository;

    @Mock
    TopicRepository topicRepository;

    @Mock
    RoadmapRepository roadmapRepository;

    @Mock
    RoadmapStepRepository roadmapStepRepository;

    @Mock
    RoadmapStepResourceRepository roadmapStepResourceRepository;

    @Mock
    RoadmapMapper roadmapMapper;

    @Mock
    QuotaService quotaService;

    @Mock
    PointService pointService;

    @Mock
    NotificationService notificationService;

    RoadmapService roadmapService;
    User user;
    StudyWorkspace workspace;
    LearningStructureVersion structureVersion;

    @BeforeEach
    void setUp() {
        roadmapService = new RoadmapService(
                workspaceRepository,
                structureVersionRepository,
                topicRepository,
                roadmapRepository,
                roadmapStepRepository,
                roadmapStepResourceRepository,
                roadmapMapper,
                quotaService,
                pointService,
                notificationService
        );
        user = user("user-1");
        workspace = workspace(user);
        structureVersion = structureVersion(workspace, 2);
    }

    @Test
    void generateCreatesVersionedRoadmapStepsResourcesAndNotification() {
        UUID workspaceId = workspace.getWorkspaceId();
        Topic first = topic("Java Basics", 1);
        Topic second = topic("Spring Boot", 2);
        Roadmap previous = roadmap(1, RoadmapStatus.ARCHIVED);
        RoadmapResponse expected = RoadmapResponse.builder()
                .workspaceId(workspaceId)
                .totalSteps(2)
                .build();
        whenOwnedWorkspace(workspaceId);
        when(structureVersionRepository.findByWorkspaceWorkspaceIdAndStatus(
                workspaceId,
                LearningStructureStatus.CONFIRMED
        )).thenReturn(List.of(structureVersion));
        when(topicRepository.findByStructureVersionStructureVersionIdOrderByChapterSequenceNoAscSequenceNoAsc(
                structureVersion.getStructureVersionId()
        )).thenReturn(List.of(first, second));
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId))
                .thenReturn(Optional.of(previous));
        when(roadmapRepository.saveAndFlush(any(Roadmap.class))).thenAnswer(invocation -> {
            Roadmap roadmap = invocation.getArgument(0);
            if (roadmap.getRoadmapId() == null) {
                roadmap.setRoadmapId(UUID.randomUUID());
            }
            return roadmap;
        });
        when(roadmapStepRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<RoadmapStep> steps = invocation.getArgument(0);
            steps.forEach(step -> step.setStepId(UUID.randomUUID()));
            return steps;
        });
        when(roadmapStepResourceRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotaService.getUnlockedRoadmapStepLimit("user-1")).thenReturn(2);
        when(roadmapMapper.toResponse(
                any(Roadmap.class),
                anyList(),
                anyList(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(expected);

        RoadmapResponse response = roadmapService.generate("user-1", workspaceId);

        assertSame(expected, response);
        verify(quotaService).validateCanGenerateAi("user-1");
        verify(notificationService).notifyRoadmapReady(user, workspace);

        ArgumentCaptor<Roadmap> roadmapCaptor = ArgumentCaptor.forClass(Roadmap.class);
        verify(roadmapRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(roadmapCaptor.capture());
        Roadmap savedRoadmap = roadmapCaptor.getAllValues().get(0);
        assertEquals(2, savedRoadmap.getVersionNo());
        assertEquals(2, savedRoadmap.getTotalSteps());
        assertEquals(BigDecimal.ZERO, savedRoadmap.getProgressPercent());
        assertEquals(RoadmapStatus.ACTIVE, savedRoadmap.getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoadmapStep>> stepCaptor = ArgumentCaptor.forClass(List.class);
        verify(roadmapStepRepository).saveAllAndFlush(stepCaptor.capture());
        List<RoadmapStep> steps = stepCaptor.getValue();
        assertEquals(2, steps.size());
        assertEquals("Java Basics", steps.get(0).getTitle());
        assertEquals(RoadmapStepStatus.CURRENT, steps.get(0).getStatus());
        assertEquals(RoadmapStepStatus.UPCOMING, steps.get(1).getStatus());
        assertTrue(steps.get(0).getEstimatedStudyTime().startsWith("45"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoadmapStepResource>> resourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(roadmapStepResourceRepository).saveAllAndFlush(resourceCaptor.capture());
        List<RoadmapStepResource> resources = resourceCaptor.getValue();
        assertEquals(6, resources.size());
        assertEquals(ResourceType.DOCUMENT_SECTION, resources.get(0).getResourceType());
        assertEquals(ResourceType.SEARCH_QUERY, resources.get(1).getResourceType());
        assertTrue(resources.get(1).getUrl().contains("youtube.com/results?search_query="));
        assertEquals(ResourceType.PRACTICE_PROMPT, resources.get(2).getResourceType());
    }

    @Test
    void generateRejectsWhenConfirmedStructureHasNoTopics() {
        UUID workspaceId = workspace.getWorkspaceId();
        whenOwnedWorkspace(workspaceId);
        when(structureVersionRepository.findByWorkspaceWorkspaceIdAndStatus(
                workspaceId,
                LearningStructureStatus.CONFIRMED
        )).thenReturn(List.of(structureVersion));
        when(topicRepository.findByStructureVersionStructureVersionIdOrderByChapterSequenceNoAscSequenceNoAsc(
                structureVersion.getStructureVersionId()
        )).thenReturn(List.of());

        AppException exception = assertThrows(
                AppException.class,
                () -> roadmapService.generate("user-1", workspaceId)
        );

        assertEquals(ErrorCode.ROADMAP_TOPICS_NOT_READY, exception.getErrorCode());
        verify(roadmapRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimRewardRequiresCompletedRoadmapAndEveryStepPointEarned() {
        UUID workspaceId = workspace.getWorkspaceId();
        Roadmap roadmap = roadmap(1, RoadmapStatus.COMPLETED);
        RoadmapStep first = step(roadmap, RoadmapStepStatus.COMPLETED);
        RoadmapStep second = step(roadmap, RoadmapStepStatus.COMPLETED);
        roadmap.setTotalSteps(2);
        whenOwnedWorkspace(workspaceId);
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId))
                .thenReturn(Optional.of(roadmap));
        when(roadmapStepRepository.findByRoadmapRoadmapIdAndStatus(
                roadmap.getRoadmapId(),
                RoadmapStepStatus.COMPLETED
        )).thenReturn(List.of(first, second));
        when(pointService.hasRoadmapStepCompletedPoints("user-1", first.getStepId())).thenReturn(true);
        when(pointService.hasRoadmapStepCompletedPoints("user-1", second.getStepId())).thenReturn(true);

        roadmapService.claimReward("user-1", workspaceId);

        verify(pointService).awardRoadmapCompleted(user, workspace, roadmap.getRoadmapId());
    }

    @Test
    void claimRewardRejectsActiveRoadmap() {
        UUID workspaceId = workspace.getWorkspaceId();
        Roadmap roadmap = roadmap(1, RoadmapStatus.ACTIVE);
        whenOwnedWorkspace(workspaceId);
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId))
                .thenReturn(Optional.of(roadmap));

        AppException exception = assertThrows(
                AppException.class,
                () -> roadmapService.claimReward("user-1", workspaceId)
        );

        assertEquals(ErrorCode.ROADMAP_NOT_FOUND, exception.getErrorCode());
        verify(pointService, never()).awardRoadmapCompleted(any(), any(), any());
    }

    private void whenOwnedWorkspace(UUID workspaceId) {
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));
    }

    private Topic topic(String title, int sequenceNo) {
        Chapter chapter = new Chapter();
        chapter.setChapterId(UUID.randomUUID());
        chapter.setTitle("Chapter " + sequenceNo);
        chapter.setSequenceNo(sequenceNo);

        Topic topic = new Topic();
        topic.setTopicId(UUID.randomUUID());
        topic.setWorkspace(workspace);
        topic.setStructureVersion(structureVersion);
        topic.setChapter(chapter);
        topic.setTitle(title);
        topic.setSummaryContent("Summary " + title);
        topic.setWhatToLearn(List.of("Learn " + title));
        topic.setKeyConcepts(List.of("Concept " + title));
        topic.setLearningOutcomes(List.of("Outcome " + title));
        topic.setRecommendedFocus(List.of("Focus " + title));
        topic.setDifficulty(DifficultyLevel.MEDIUM);
        topic.setEstimatedMinutes(45);
        topic.setSequenceNo(sequenceNo);
        return topic;
    }

    private Roadmap roadmap(int versionNo, RoadmapStatus status) {
        Roadmap roadmap = new Roadmap();
        roadmap.setRoadmapId(UUID.randomUUID());
        roadmap.setWorkspace(workspace);
        roadmap.setStructureVersion(structureVersion);
        roadmap.setUser(user);
        roadmap.setTitle("Roadmap");
        roadmap.setVersionNo(versionNo);
        roadmap.setTotalSteps(1);
        roadmap.setCompletedSteps(0);
        roadmap.setProgressPercent(BigDecimal.ZERO);
        roadmap.setStatus(status);
        return roadmap;
    }

    private RoadmapStep step(Roadmap roadmap, RoadmapStepStatus status) {
        RoadmapStep step = new RoadmapStep();
        step.setStepId(UUID.randomUUID());
        step.setRoadmap(roadmap);
        step.setWorkspace(workspace);
        step.setTitle("Step");
        step.setSequenceNo(1);
        step.setStatus(status);
        return step;
    }

    private LearningStructureVersion structureVersion(StudyWorkspace workspace, int versionNo) {
        LearningStructureVersion version = new LearningStructureVersion();
        version.setStructureVersionId(UUID.randomUUID());
        version.setWorkspace(workspace);
        version.setVersionNo(versionNo);
        version.setStatus(LearningStructureStatus.CONFIRMED);
        return version;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName("Java");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
