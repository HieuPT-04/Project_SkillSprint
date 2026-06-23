package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.calendar.CreateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.GenerateCalendarRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskStatusRequest;
import com.skillsprint.dto.response.calendar.CalendarScheduleRunResponse;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.calendar.EisenhowerBoardResponse;
import com.skillsprint.dto.response.calendar.EisenhowerQuadrantResponse;
import com.skillsprint.dto.response.learningstructure.ChapterResponse;
import com.skillsprint.dto.response.learningstructure.LearningStructureResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResponse;
import com.skillsprint.dto.response.roadmap.RoadmapStepResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.calendar.CalendarScheduleRunStatus;
import com.skillsprint.enums.calendar.CalendarScheduleScope;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.calendar.EisenhowerQuadrant;
import com.skillsprint.enums.calendar.WeekDay;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.learningstructure.GeneratedBy;
import com.skillsprint.enums.learningstructure.LearningStructureStatus;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.calendar.CalendarService;
import com.skillsprint.service.learningstructure.LearningStructureService;
import com.skillsprint.service.roadmap.RoadmapService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningRoadmapCalendarApiFlowTest {

    private static final String USER_ID = "learning-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    LearningStructureService learningStructureService;

    @MockBean
    RoadmapService roadmapService;

    @MockBean
    CalendarService calendarService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID workspaceId;
    UUID structureVersionId;
    UUID roadmapId;
    UUID stepId;
    UUID taskId;
    UUID runId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        structureVersionId = UUID.randomUUID();
        roadmapId = UUID.randomUUID();
        stepId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        runId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void anonymousUserCannotUseLearningRoadmapCalendarEndpoints() throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/learning-structure/generate", workspaceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/roadmaps/generate", workspaceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/calendar/tasks", workspaceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(learningStructureService, never()).generate(any(), any());
        verify(roadmapService, never()).generate(any(), any());
        verify(calendarService, never()).getTasks(any(), any());
    }

    @Test
    void learningStructureEndpointsReturnExpectedShapesAndMapErrors() throws Exception {
        when(learningStructureService.generate(USER_ID, workspaceId)).thenReturn(learningStructureResponse());
        when(learningStructureService.getLatest(USER_ID, workspaceId)).thenReturn(learningStructureResponse());
        when(learningStructureService.confirm(USER_ID, workspaceId)).thenReturn(learningStructureResponse());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/learning-structure/generate", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo cấu trúc học tập thành công"))
                .andExpect(jsonPath("$.data.structureVersionId").value(structureVersionId.toString()))
                .andExpect(jsonPath("$.data.status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.chapters[0].title").value("Java Basics"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/learning-structure", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/learning-structure/confirm", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xác nhận cấu trúc học tập thành công"));

        when(learningStructureService.getLatest(USER_ID, workspaceId))
                .thenThrow(new AppException(ErrorCode.LEARNING_STRUCTURE_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/learning-structure", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void roadmapEndpointsReturnExpectedShapesAndMapLockedStepErrors() throws Exception {
        when(roadmapService.generate(USER_ID, workspaceId)).thenReturn(roadmapResponse());
        when(roadmapService.getCurrent(USER_ID, workspaceId)).thenReturn(roadmapResponse());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/roadmaps/generate", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo roadmap thành công"))
                .andExpect(jsonPath("$.data.roadmapId").value(roadmapId.toString()))
                .andExpect(jsonPath("$.data.steps[0].stepId").value(stepId.toString()))
                .andExpect(jsonPath("$.data.steps[0].locked").value(false));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/roadmaps/current", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Java Roadmap"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/roadmaps/claim-reward", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nhận phần thưởng thành công"));

        verify(roadmapService).claimReward(USER_ID, workspaceId);

        when(roadmapService.getCurrent(USER_ID, workspaceId))
                .thenThrow(new AppException(ErrorCode.QUOTA_ROADMAP_STEP_LOCKED));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/roadmaps/current", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void calendarGenerateValidationAndSuccessFlowWork() throws Exception {
        when(calendarService.generate(eq(USER_ID), eq(workspaceId), any(GenerateCalendarRequest.class)))
                .thenReturn(scheduleRunResponse());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/calendar/generate", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-06-24",
                                  "endDate": "2026-06-30",
                                  "studyDays": ["MONDAY", "WEDNESDAY"],
                                  "dailyStartTime": "19:00:00",
                                  "sessionMinutes": 45,
                                  "sessionsPerDay": 2,
                                  "includeReviewSessions": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo lịch học thành công"))
                .andExpect(jsonPath("$.data.runId").value(runId.toString()))
                .andExpect(jsonPath("$.data.tasks[0].taskId").value(taskId.toString()));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/calendar/generate", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionMinutes": 5,
                                  "sessionsPerDay": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void calendarTaskEndpointsReturnExpectedShapes() throws Exception {
        when(calendarService.getTasks(USER_ID, workspaceId)).thenReturn(List.of(calendarTaskResponse()));
        when(calendarService.getEisenhowerBoard(eq(USER_ID), eq(workspaceId), any())).thenReturn(eisenhowerBoard());
        when(calendarService.getEisenhowerTasksForWorkspace(USER_ID, workspaceId)).thenReturn(eisenhowerBoard());
        when(calendarService.createTask(eq(USER_ID), eq(workspaceId), any(CreateCalendarTaskRequest.class)))
                .thenReturn(calendarTaskResponse());
        when(calendarService.updateTaskStatus(eq(USER_ID), eq(workspaceId), eq(taskId), any(UpdateCalendarTaskStatusRequest.class)))
                .thenReturn(completedTaskResponse());
        when(calendarService.updateTask(eq(USER_ID), eq(taskId), any(UpdateCalendarTaskRequest.class)))
                .thenReturn(calendarTaskResponse());
        when(calendarService.completeTask(USER_ID, taskId)).thenReturn(completedTaskResponse());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/calendar/tasks", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskId").value(taskId.toString()));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/calendar/eisenhower", workspaceId)
                        .param("date", "2026-06-24")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy Eisenhower board thành công"))
                .andExpect(jsonPath("$.data.totalTasks").value(1));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/eisenhower-tasks", workspaceId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy Eisenhower tasks thành công"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/calendar/tasks", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Read chapter",
                                  "quadrant": "DO_NOW",
                                  "taskDate": "2026-06-24",
                                  "startTime": "19:00:00",
                                  "endTime": "19:45:00",
                                  "durationMinutes": 45
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo task thành công"));

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/calendar/tasks/{taskId}/status", workspaceId, taskId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái task thành công"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(patch("/api/calendar/tasks/{taskId}", taskId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskDate": "2026-06-25",
                                  "startTime": "20:00:00",
                                  "endTime": "20:45:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật lịch học thành công"));

        mockMvc.perform(patch("/api/calendar/tasks/{taskId}/complete", taskId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hoàn thành task học thành công"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void calendarTaskValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(post("/api/workspaces/{workspaceId}/calendar/tasks", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        when(calendarService.updateTaskStatus(eq(USER_ID), eq(workspaceId), eq(taskId), any(UpdateCalendarTaskStatusRequest.class)))
                .thenThrow(new AppException(ErrorCode.CALENDAR_TASK_ALREADY_COMPLETED));

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/calendar/tasks/{taskId}/status", workspaceId, taskId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "TODO"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private LearningStructureResponse learningStructureResponse() {
        return LearningStructureResponse.builder()
                .structureVersionId(structureVersionId)
                .workspaceId(workspaceId)
                .versionNo(1)
                .status(LearningStructureStatus.REVIEW_REQUIRED)
                .generatedBy(GeneratedBy.AI)
                .aiModel("gemini-test")
                .confidenceScore(new BigDecimal("0.85"))
                .inputSummary("Java basics")
                .warnings(List.of("Review suggested"))
                .chapters(List.of(ChapterResponse.builder()
                        .chapterId(UUID.randomUUID())
                        .title("Java Basics")
                        .summary("Intro")
                        .difficulty(DifficultyLevel.EASY)
                        .estimatedMinutes(60)
                        .sequenceNo(1)
                        .topics(List.of())
                        .build()))
                .build();
    }

    private RoadmapResponse roadmapResponse() {
        return RoadmapResponse.builder()
                .roadmapId(roadmapId)
                .workspaceId(workspaceId)
                .structureVersionId(structureVersionId)
                .currentStepId(stepId)
                .title("Java Roadmap")
                .description("Learn Java")
                .totalSteps(1)
                .completedSteps(0)
                .progressPercent(BigDecimal.ZERO)
                .versionNo(1)
                .status(RoadmapStatus.ACTIVE)
                .isRewardClaimed(false)
                .steps(List.of(RoadmapStepResponse.builder()
                        .stepId(stepId)
                        .title("Read Java basics")
                        .difficulty(DifficultyLevel.EASY)
                        .sequenceNo(1)
                        .status(RoadmapStepStatus.CURRENT)
                        .locked(false)
                        .resources(List.of())
                        .build()))
                .build();
    }

    private CalendarScheduleRunResponse scheduleRunResponse() {
        return CalendarScheduleRunResponse.builder()
                .runId(runId)
                .workspaceId(workspaceId)
                .roadmapId(roadmapId)
                .scheduleScope(CalendarScheduleScope.FULL_ROADMAP)
                .startDate(LocalDate.parse("2026-06-24"))
                .endDate(LocalDate.parse("2026-06-30"))
                .availableDays(List.of(WeekDay.MONDAY, WeekDay.WEDNESDAY))
                .preferredSessionMinutes(45)
                .maxSessionsPerDay(2)
                .includeReviewSessions(true)
                .status(CalendarScheduleRunStatus.PREVIEW)
                .tasks(List.of(calendarTaskResponse()))
                .build();
    }

    private CalendarTaskResponse calendarTaskResponse() {
        return CalendarTaskResponse.builder()
                .taskId(taskId)
                .workspaceId(workspaceId)
                .roadmapId(roadmapId)
                .roadmapStepId(stepId)
                .title("Read chapter")
                .description("Read Java basics")
                .taskDate(LocalDate.parse("2026-06-24"))
                .startTime(LocalTime.parse("19:00:00"))
                .endTime(LocalTime.parse("19:45:00"))
                .durationMinutes(45)
                .category(CalendarTaskCategory.DEEP_STUDY)
                .priority(CalendarTaskPriority.MEDIUM)
                .status(CalendarTaskStatus.TODO)
                .source(CalendarTaskSource.AI_GENERATED)
                .important(true)
                .urgent(true)
                .eisenhowerQuadrant(EisenhowerQuadrant.DO_NOW)
                .xpReward(10)
                .overdue(false)
                .studySessionEndpoint("/api/calendar/tasks/%s/study-session".formatted(taskId))
                .build();
    }

    private CalendarTaskResponse completedTaskResponse() {
        return CalendarTaskResponse.builder()
                .taskId(taskId)
                .workspaceId(workspaceId)
                .title("Read chapter")
                .status(CalendarTaskStatus.COMPLETED)
                .build();
    }

    private EisenhowerBoardResponse eisenhowerBoard() {
        return EisenhowerBoardResponse.builder()
                .workspaceId(workspaceId)
                .date(LocalDate.parse("2026-06-24"))
                .totalTasks(1)
                .completedTasks(0)
                .pendingTasks(1)
                .quadrants(List.of(EisenhowerQuadrantResponse.builder()
                        .quadrant(EisenhowerQuadrant.DO_NOW)
                        .title("Do now")
                        .description("Important and urgent")
                        .taskCount(1)
                        .tasks(List.of(calendarTaskResponse()))
                        .build()))
                .build();
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("learning-flow@example.com");
        user.setFullName("Learning Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
