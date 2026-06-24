package com.skillsprint.service.roadmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    void generateCompactsOversizedReportStructureAndKeepsUsefulResources() {
        UUID workspaceId = workspace.getWorkspaceId();
        List<Topic> topics = reportTopics(43);
        RoadmapResponse expected = RoadmapResponse.builder()
                .workspaceId(workspaceId)
                .build();
        whenOwnedWorkspace(workspaceId);
        when(structureVersionRepository.findByWorkspaceWorkspaceIdAndStatus(
                workspaceId,
                LearningStructureStatus.CONFIRMED
        )).thenReturn(List.of(structureVersion));
        when(topicRepository.findByStructureVersionStructureVersionIdOrderByChapterSequenceNoAscSequenceNoAsc(
                structureVersion.getStructureVersionId()
        )).thenReturn(topics);
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId))
                .thenReturn(Optional.empty());
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
        when(quotaService.getUnlockedRoadmapStepLimit("user-1")).thenReturn(15);
        when(roadmapMapper.toResponse(
                any(Roadmap.class),
                anyList(),
                anyList(),
                org.mockito.ArgumentMatchers.eq(15),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(expected);

        roadmapService.generate("user-1", workspaceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoadmapStep>> stepCaptor = ArgumentCaptor.forClass(List.class);
        verify(roadmapStepRepository).saveAllAndFlush(stepCaptor.capture());
        List<RoadmapStep> steps = stepCaptor.getValue();
        assertTrue(steps.size() <= RoadmapService.REPORT_ROADMAP_TARGET_MAX_STEPS);
        assertNotEquals(43, steps.size());
        assertEquals(RoadmapStepStatus.CURRENT, steps.get(0).getStatus());
        assertTrue(steps.stream().skip(1).allMatch(step -> step.getStatus() == RoadmapStepStatus.UPCOMING));
        assertEquals(steps.size(), steps.stream().map(RoadmapStep::getTitle).distinct().count());

        Set<String> forbiddenTitles = Set.of(
                "Vấn đề",
                "Cách sửa",
                "File liên quan",
                "Summary",
                "Root Cause",
                "selected slot",
                "selected slots",
                "08:00 - 10:00"
        );
        assertTrue(steps.stream().noneMatch(step -> forbiddenTitles.contains(step.getTitle())));
        assertTrue(steps.stream().noneMatch(step -> step.getTitle().matches("\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}")));
        assertTrue(steps.stream().anyMatch(step -> step.getTitle().contains("Hiểu")
                || step.getTitle().contains("Phân tích")
                || step.getTitle().contains("Kiểm thử")));

        String combinedContent = steps.stream()
                .map(step -> String.join(" ",
                        step.getTitle(),
                        step.getSummary() == null ? "" : step.getSummary(),
                        String.join(" ", step.getWhatToLearn()),
                        String.join(" ", step.getKeyConcepts()),
                        String.join(" ", step.getLearningOutcomes()),
                        String.join(" ", step.getRecommendedFocus())
                ))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        assertTrue(combinedContent.contains("availability"));
        assertTrue(combinedContent.contains("MAX_TOKENS"));
        assertTrue(combinedContent.contains("Build / Test result"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoadmapStepResource>> resourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(roadmapStepResourceRepository).saveAllAndFlush(resourceCaptor.capture());
        List<RoadmapStepResource> resources = resourceCaptor.getValue();
        assertNotEquals(129, resources.size());
        assertTrue(resources.size() <= 45);
        assertTrue(resources.size() >= steps.size() * 2);
        assertEquals(resources.size(), resources.stream()
                .map(this::resourceKey)
                .distinct()
                .count());
        assertTrue(resources.stream()
                .noneMatch(resource -> rawResourceFragment(resource, forbiddenTitles)));

        ArgumentCaptor<Roadmap> roadmapCaptor = ArgumentCaptor.forClass(Roadmap.class);
        verify(roadmapRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(roadmapCaptor.capture());
        assertEquals(steps.size(), roadmapCaptor.getAllValues().get(0).getTotalSteps());
    }

    @Test
    void generateDeduplicatesResourcesButKeepsUsefulStepResources() {
        UUID workspaceId = workspace.getWorkspaceId();
        Topic first = topic("Caching Basics", 1);
        Topic second = topic("Caching Basics", 2);
        first.setSummaryContent("Shared summary");
        second.setSummaryContent("Shared summary");
        first.setWhatToLearn(List.of("Understand cache keys"));
        second.setWhatToLearn(List.of("Understand cache keys"));
        first.setKeyConcepts(List.of("Cache key"));
        second.setKeyConcepts(List.of("Cache key"));
        first.setLearningOutcomes(List.of("Explain cache invalidation"));
        second.setLearningOutcomes(List.of("Explain cache invalidation"));
        first.setRecommendedFocus(List.of("Avoid stale data"));
        second.setRecommendedFocus(List.of("Avoid stale data"));
        RoadmapResponse expected = RoadmapResponse.builder().workspaceId(workspaceId).build();
        whenOwnedWorkspace(workspaceId);
        when(structureVersionRepository.findByWorkspaceWorkspaceIdAndStatus(
                workspaceId,
                LearningStructureStatus.CONFIRMED
        )).thenReturn(List.of(structureVersion));
        when(topicRepository.findByStructureVersionStructureVersionIdOrderByChapterSequenceNoAscSequenceNoAsc(
                structureVersion.getStructureVersionId()
        )).thenReturn(List.of(first, second));
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId))
                .thenReturn(Optional.empty());
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

        roadmapService.generate("user-1", workspaceId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoadmapStepResource>> resourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(roadmapStepResourceRepository).saveAllAndFlush(resourceCaptor.capture());
        List<RoadmapStepResource> resources = resourceCaptor.getValue();
        assertFalse(resources.isEmpty());
        assertTrue(resources.size() < 6);
        assertEquals(resources.size(), resources.stream()
                .map(this::resourceKey)
                .distinct()
                .count());
        assertTrue(resources.stream().anyMatch(resource -> resource.getResourceType() == ResourceType.DOCUMENT_SECTION));
        assertTrue(resources.stream().anyMatch(resource -> resource.getResourceType() == ResourceType.PRACTICE_PROMPT));
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

    private List<Topic> reportTopics(int count) {
        List<String> titles = List.of(
                "Tổng quan",
                "Lỗi 1: Calendar chỉ dùng selected slot đầu tiên",
                "Vấn đề",
                "Root Cause",
                "Impact",
                "Cách sửa",
                "File liên quan",
                "Thay đổi chính",
                "Fallback khi AI lỗi",
                "Prompt Gemini",
                "Gemini request config",
                "Tests đã thêm/cập nhật",
                "Build / Test result",
                "Verification",
                "Kết luận",
                "Final Result"
        );
        List<Topic> topics = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String title = titles.get(i % titles.size());
            Topic topic = topic(title, i + 1);
            topic.getChapter().setTitle(reportChapterTitle(title));
            topic.setSummaryContent(reportSummary(title, i));
            topic.setWhatToLearn(List.of(
                    "Hiểu nội dung " + title,
                    "Giữ trace phần " + (i + 1)
            ));
            topic.setKeyConcepts(List.of(
                    title,
                    i % 3 == 0 ? "availability" : "Calendar AI"
            ));
            topic.setLearningOutcomes(List.of("Trình bày lại " + title));
            topic.setRecommendedFocus(List.of(i % 5 == 0 ? "MAX_TOKENS" : "regression tests"));
            topic.setEstimatedMinutes(15);
            topics.add(topic);
        }
        return topics;
    }

    private String reportChapterTitle(String title) {
        if (title.contains("Tổng quan")) {
            return "Tổng quan";
        }
        if (title.contains("Lỗi") || title.contains("Vấn đề") || title.contains("Root Cause") || title.contains("Impact")) {
            return "Nguyên nhân và tác động";
        }
        if (title.contains("Fallback") || title.contains("Gemini")) {
            return "Kiểm soát AI và fallback";
        }
        if (title.contains("Tests") || title.contains("Build") || title.contains("Verification")) {
            return "Kiểm thử và xác minh";
        }
        if (title.contains("Kết luận") || title.contains("Final Result")) {
            return "Kết quả cuối cùng";
        }
        return "Cách khắc phục";
    }

    private String reportSummary(String title, int index) {
        String base = title + " trong báo cáo Calendar AI. ";
        return switch (index % 6) {
            case 0 -> base + "Calendar phải đọc đủ availability, không chỉ selected slot.";
            case 1 -> base + "Lỗi selected slots làm lịch sinh ngoài availability.";
            case 2 -> base + "Cần parse toàn bộ time windows 08:00 - 10:00 và các slot kế tiếp.";
            case 3 -> base + "Rule-based planner phải validate availability trước khi lưu.";
            case 4 -> base + "Gemini MAX_TOKENS có thể làm JSON cụt nên cần fallback an toàn.";
            default -> base + "Build / Test result và regression tests phải xác minh case 1 slot, 2 slot, 3 slot.";
        };
    }

    private String resourceKey(RoadmapStepResource resource) {
        return String.join("|",
                resource.getResourceType() == null ? "" : resource.getResourceType().name(),
                normalize(resource.getTitle()),
                normalize(resource.getUrl()),
                normalize(resource.getSearchQuery()),
                normalize(resource.getContent())
        );
    }

    private boolean rawResourceFragment(RoadmapStepResource resource, Set<String> forbiddenTitles) {
        return forbiddenTitles.stream().anyMatch(fragment ->
                fragment.equals(resource.getTitle())
                        || fragment.equals(resource.getSearchQuery())
                        || fragment.equals(resource.getContent())
                        || fragment.equals(resource.getUrl()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
