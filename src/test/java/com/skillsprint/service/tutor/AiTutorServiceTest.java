package com.skillsprint.service.tutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.configuration.ai.GeminiProperties;
import com.skillsprint.dto.request.tutor.TutorAskRequest;
import com.skillsprint.dto.response.tutor.TutorAskResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.tutor.ai.AiTutorDraft;
import com.skillsprint.service.tutor.ai.GeminiTutorClient;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiTutorServiceTest {

    @Mock
    RoadmapStepRepository roadmapStepRepository;

    @Mock
    RoadmapRepository roadmapRepository;

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    MaterialChunkRepository materialChunkRepository;

    @Mock
    CalendarTaskRepository calendarTaskRepository;

    @Mock
    GeminiTutorClient geminiTutorClient;

    @Mock
    QuotaService quotaService;

    AiTutorService aiTutorService;
    User user;
    StudyWorkspace workspace;
    RoadmapStep step;

    @BeforeEach
    void setUp() {
        aiTutorService = new AiTutorService(
                roadmapStepRepository,
                roadmapRepository,
                workspaceRepository,
                materialChunkRepository,
                calendarTaskRepository,
                geminiTutorClient,
                new GeminiProperties(true, "key", "gemini-test", "https://gemini.example", 2000),
                quotaService
        );
        user = user("user-1");
        workspace = workspace(user);
        step = step("Polymorphism", "Java polymorphism lets objects respond through a shared parent type.");
    }

    @Test
    void askRejectsBlankQuestionBeforeLoadingStep() {
        AppException exception = assertThrows(
                AppException.class,
                () -> aiTutorService.ask("user-1", step.getStepId(), request("   "))
        );

        assertEquals(ErrorCode.TUTOR_QUESTION_REQUIRED, exception.getErrorCode());
        verify(roadmapStepRepository, never()).findById(step.getStepId());
        verify(geminiTutorClient, never()).ask(anyString(), anyString());
    }

    @Test
    void askReturnsLocalClarificationForLowSignalQuestionWithoutCallingGemini() {
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));

        TutorAskResponse response = aiTutorService.ask("user-1", step.getStepId(), request("Ê"));

        assertEquals("Bạn hãy nhập một câu hỏi cụ thể về nội dung bài học hoặc roadmap.", response.getAnswer());
        assertEquals("LOW", response.getConfidence());
        verify(materialChunkRepository, never())
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId());
        verify(geminiTutorClient, never()).ask(anyString(), anyString());
    }

    @Test
    void askWorkspaceReturnsLocalClarificationForLowSignalQuestionWithoutBuildingAiContext() {
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspace.getWorkspaceId(),
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));

        TutorAskResponse response = aiTutorService.askWorkspace("user-1", workspace.getWorkspaceId(), request("Ê"));

        assertEquals("Bạn hãy nhập một câu hỏi cụ thể về nội dung bài học hoặc roadmap.", response.getAnswer());
        assertEquals("WORKSPACE", response.getContext().getScope());
        verify(roadmapRepository, never()).findByWorkspaceWorkspaceId(workspace.getWorkspaceId());
        verify(geminiTutorClient, never()).ask(anyString(), anyString());
    }

    @Test
    void askBuildsContextTrimsQuestionAndCompactsAiAnswer() {
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId()))
                .thenReturn(List.of(chunk("Polymorphism is used when subclasses override behavior.")));
        when(geminiTutorClient.ask(eq("What is polymorphism?"), contains("Roadmap step")))
                .thenReturn(new AiTutorDraft(
                        "**Polymorphism** lets one interface call different implementations.\n- Override methods for concrete behavior.",
                        List.of("How do overrides work?", "Give me a Java example", "When should I use it?"),
                        "HIGH"
                ));

        TutorAskResponse response = aiTutorService.ask("user-1", step.getStepId(), request("  What is polymorphism?  "));

        assertEquals(
                "Polymorphism lets one interface call different implementations. Override methods for concrete behavior.",
                response.getAnswer()
        );
        assertEquals("HIGH", response.getConfidence());
        assertEquals("ROADMAP_STEP", response.getContext().getScope());
        assertEquals(step.getStepId(), response.getContext().getMatchedStepId());
        assertEquals(3, response.getSuggestedQuestions().size());
        verify(quotaService).validateFeature("user-1", PlanFeatureKeys.AI_TUTOR);
        verify(quotaService).validateCanAccessRoadmapStep("user-1", step);
    }

    @Test
    void askFallsBackWhenAiDraftIsInvalid() {
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId()))
                .thenReturn(List.of(chunk("Enough context about polymorphism and method overriding.")));
        when(geminiTutorClient.ask(eq("Explain it"), contains("Roadmap step")))
                .thenReturn(new AiTutorDraft("", List.of(), "HIGH"));

        TutorAskResponse response = aiTutorService.ask("user-1", step.getStepId(), request("Explain it"));

        assertEquals("LOW", response.getConfidence());
        assertEquals("ROADMAP_STEP", response.getContext().getScope());
        assertEquals(3, response.getSuggestedQuestions().size());
        assertTrue(response.getAnswer().length() > 30);
    }

    @Test
    void askWorkspaceMatchesRelevantAccessibleStepAndIncludesTodayTasks() {
        RoadmapStep other = step("Collections", "Lists and maps store groups of data.");
        Roadmap roadmap = roadmap(step);
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspace.getWorkspaceId(),
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));
        when(roadmapRepository.findByWorkspaceWorkspaceId(workspace.getWorkspaceId())).thenReturn(List.of(roadmap));
        when(roadmapStepRepository.findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId()))
                .thenReturn(List.of(other, step));
        when(quotaService.getUnlockedRoadmapStepLimit("user-1")).thenReturn(10);
        when(calendarTaskRepository.findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
                eq(workspace.getWorkspaceId()),
                eq("user-1"),
                eq(LocalDate.now())
        )).thenReturn(List.of(todayTask()));
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId()))
                .thenReturn(List.of(chunk("Polymorphism means many forms in object-oriented design.")));
        when(geminiTutorClient.ask(eq("How does polymorphism help?"), contains("Today calendar tasks")))
                .thenReturn(new AiTutorDraft(
                        "It helps the same code call different subclass behavior safely.",
                        List.of("Show an example", "What is overriding?", "How is it different from overloading?"),
                        "HIGH"
                ));

        TutorAskResponse response = aiTutorService.askWorkspace(
                "user-1",
                workspace.getWorkspaceId(),
                request("How does polymorphism help?")
        );

        assertNotNull(response.getContext());
        assertEquals("WORKSPACE", response.getContext().getScope());
        assertEquals(step.getStepId(), response.getContext().getMatchedStepId());
        assertEquals("HIGH", response.getConfidence());
    }

    private TutorAskRequest request(String question) {
        TutorAskRequest request = new TutorAskRequest();
        request.setQuestion(question);
        return request;
    }

    private MaterialChunk chunk(String content) {
        MaterialChunk chunk = new MaterialChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setWorkspace(workspace);
        chunk.setSectionTitle("Section");
        chunk.setContent(content);
        chunk.setSummary(content);
        chunk.setChunkIndex(1);
        return chunk;
    }

    private CalendarTask todayTask() {
        CalendarTask task = new CalendarTask();
        task.setTaskId(UUID.randomUUID());
        task.setWorkspace(workspace);
        task.setUser(user);
        task.setTitle("Review polymorphism");
        task.setTaskDate(LocalDate.now());
        task.setStartTime(LocalTime.parse("19:00"));
        task.setEndTime(LocalTime.parse("20:00"));
        task.setCategory(CalendarTaskCategory.DEEP_STUDY);
        task.setPriority(CalendarTaskPriority.MEDIUM);
        task.setStatus(CalendarTaskStatus.TODO);
        return task;
    }

    private Roadmap roadmap(RoadmapStep currentStep) {
        Roadmap roadmap = new Roadmap();
        roadmap.setRoadmapId(UUID.randomUUID());
        roadmap.setWorkspace(workspace);
        roadmap.setUser(user);
        roadmap.setTitle("Java Roadmap");
        roadmap.setVersionNo(1);
        roadmap.setStatus(RoadmapStatus.ACTIVE);
        roadmap.setCurrentStep(currentStep);
        return roadmap;
    }

    private RoadmapStep step(String title, String summary) {
        RoadmapStep step = new RoadmapStep();
        step.setStepId(UUID.randomUUID());
        step.setWorkspace(workspace);
        step.setTitle(title);
        step.setSubtitle("Java");
        step.setSummary(summary);
        step.setKeyConcepts(List.of(title, "override"));
        step.setLearningOutcomes(List.of("Explain " + title));
        step.setSequenceNo(title.equals("Polymorphism") ? 2 : 1);
        return step;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName("Java");
        workspace.setDescription("Backend Java");
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
