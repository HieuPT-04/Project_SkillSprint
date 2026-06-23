package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.quiz.SubmitQuizRequest;
import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.request.session.StartStudySessionRequest;
import com.skillsprint.dto.request.tutor.TutorAskRequest;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.quiz.QuizAnswerResultResponse;
import com.skillsprint.dto.response.quiz.QuizAttemptResponse;
import com.skillsprint.dto.response.quiz.QuizOptionResponse;
import com.skillsprint.dto.response.quiz.QuizQuestionResponse;
import com.skillsprint.dto.response.quiz.QuizResponse;
import com.skillsprint.dto.response.session.StudySessionDetailResponse;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.dto.response.tutor.TutorAskResponse;
import com.skillsprint.dto.response.tutor.TutorContextResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskSource;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.quiz.QuizQuestionType;
import com.skillsprint.enums.quiz.QuizStatus;
import com.skillsprint.enums.roadmap.RoadmapStepStatus;
import com.skillsprint.enums.session.PomodoroPhase;
import com.skillsprint.enums.session.PomodoroSessionStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.quiz.QuizService;
import com.skillsprint.service.session.StudySessionService;
import com.skillsprint.service.tutor.AiTutorService;
import java.time.Instant;
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
class StudyQuizTutorApiFlowTest {

    private static final String USER_ID = "study-quiz-tutor-flow-user";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    StudySessionService studySessionService;

    @MockBean
    QuizService quizService;

    @MockBean
    AiTutorService aiTutorService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID workspaceId;
    UUID taskId;
    UUID sessionId;
    UUID stepId;
    UUID quizId;
    UUID questionId;
    UUID optionId;
    UUID attemptId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        stepId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        optionId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void anonymousUserCannotUseStudyQuizTutorEndpoints() throws Exception {
        mockMvc.perform(get("/api/calendar/tasks/{taskId}/study-session", taskId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/roadmap-steps/{stepId}/quiz/generate", stepId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/tutor/ask", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Explain this roadmap"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionService, never()).getStudySessionDetail(any(), any());
        verify(quizService, never()).generate(any(), any());
        verify(aiTutorService, never()).askWorkspace(any(), any(), any());
    }

    @Test
    void studySessionEndpointsReturnExpectedShapesAndMessages() throws Exception {
        when(studySessionService.getStudySessionDetail(USER_ID, taskId)).thenReturn(studySessionDetailResponse());
        when(studySessionService.startSession(eq(USER_ID), eq(taskId), any(StartStudySessionRequest.class)))
                .thenReturn(inProgressSessionResponse());
        when(studySessionService.getSessionDetail(USER_ID, sessionId)).thenReturn(studySessionDetailResponse());
        when(studySessionService.pausePomodoro(USER_ID, sessionId)).thenReturn(pausedSessionResponse());
        when(studySessionService.resumePomodoro(USER_ID, sessionId)).thenReturn(inProgressSessionResponse());
        when(studySessionService.nextPomodoroPhase(USER_ID, sessionId)).thenReturn(shortBreakSessionResponse());
        when(studySessionService.finishPomodoro(USER_ID, sessionId)).thenReturn(completedSessionResponse(true));
        when(studySessionService.finishSession(eq(USER_ID), eq(sessionId), any(FinishStudySessionRequest.class)))
                .thenReturn(completedSessionResponse(true))
                .thenReturn(completedSessionResponse(false));

        mockMvc.perform(get("/api/calendar/tasks/{taskId}/study-session", taskId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy phiên học thành công"))
                .andExpect(jsonPath("$.data.task.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.data.actions.canFinish").value(true));

        mockMvc.perform(post("/api/calendar/tasks/{taskId}/sessions/start", taskId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usePomodoro": true,
                                  "focusMinutes": 25,
                                  "shortBreakMinutes": 5,
                                  "longBreakMinutes": 15,
                                  "totalCycles": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bắt đầu phiên học thành công"))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.pomodoro.currentPhase").value("FOCUS"));

        mockMvc.perform(get("/api/study-sessions/{sessionId}", sessionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy chi tiết phiên học thành công"));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/pomodoro/pause", sessionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạm dừng Pomodoro thành công"))
                .andExpect(jsonPath("$.data.pomodoro.status").value("PAUSED"));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/pomodoro/resume", sessionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tiếp tục Pomodoro thành công"));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/pomodoro/next-phase", sessionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chuyển Pomodoro phase thành công"))
                .andExpect(jsonPath("$.data.pomodoro.currentPhase").value("SHORT_BREAK"));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/pomodoro/finish", sessionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Kết thúc Pomodoro thành công"));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/finish", sessionId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "Finished practice",
                                  "focusScore": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hoàn thành buổi học thành công"))
                .andExpect(jsonPath("$.data.taskCompleted").value(true));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/finish", sessionId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "Stopped early",
                                  "focusScore": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Phiên học đã kết thúc, task chưa hoàn thành vì thời gian học chưa đủ"))
                .andExpect(jsonPath("$.data.taskCompleted").value(false));
    }

    @Test
    void studySessionValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(post("/api/calendar/tasks/{taskId}/sessions/start", taskId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usePomodoro": true,
                                  "focusMinutes": 1,
                                  "shortBreakMinutes": 0,
                                  "longBreakMinutes": 200,
                                  "totalCycles": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        when(studySessionService.pausePomodoro(USER_ID, sessionId))
                .thenThrow(new AppException(ErrorCode.POMODORO_SESSION_NOT_RUNNING));

        mockMvc.perform(post("/api/study-sessions/{sessionId}/pomodoro/pause", sessionId)
                        .with(learnerJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void quizEndpointsReturnExpectedShapesAndMapErrors() throws Exception {
        when(quizService.generate(USER_ID, stepId)).thenReturn(quizResponse());
        when(quizService.getCurrent(USER_ID, stepId)).thenReturn(quizResponse());
        when(quizService.submit(eq(USER_ID), eq(quizId), any(SubmitQuizRequest.class))).thenReturn(attemptResponse());
        when(quizService.getLatestAttempt(USER_ID, quizId)).thenReturn(attemptResponse());

        mockMvc.perform(post("/api/roadmap-steps/{stepId}/quiz/generate", stepId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tạo quiz thành công"))
                .andExpect(jsonPath("$.data.quizId").value(quizId.toString()))
                .andExpect(jsonPath("$.data.questions[0].options[0].optionId").value(optionId.toString()));

        mockMvc.perform(get("/api/roadmap-steps/{stepId}/quiz/current", stepId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy quiz thành công"));

        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quizId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {
                                      "questionId": "%s",
                                      "selectedOptionId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(questionId, optionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nộp quiz thành công"))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.score").value(100));

        mockMvc.perform(get("/api/quizzes/{quizId}/attempts/latest", quizId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Lấy kết quả quiz thành công"))
                .andExpect(jsonPath("$.data.attemptId").value(attemptId.toString()));

        when(quizService.getCurrent(USER_ID, stepId)).thenThrow(new AppException(ErrorCode.QUIZ_NOT_FOUND));

        mockMvc.perform(get("/api/roadmap-steps/{stepId}/quiz/current", stepId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void quizSubmitValidationIsMapped() throws Exception {
        mockMvc.perform(post("/api/quizzes/{quizId}/submit", quizId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void tutorEndpointsReturnExpectedShapesAndValidationErrors() throws Exception {
        when(aiTutorService.askWorkspace(eq(USER_ID), eq(workspaceId), any(TutorAskRequest.class)))
                .thenReturn(workspaceTutorResponse());
        when(aiTutorService.ask(eq(USER_ID), eq(stepId), any(TutorAskRequest.class)))
                .thenReturn(stepTutorResponse());

        mockMvc.perform(post("/api/workspaces/{workspaceId}/tutor/ask", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "How should I study this workspace?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI Tutor đã trả lời"))
                .andExpect(jsonPath("$.data.context.scope").value("WORKSPACE"))
                .andExpect(jsonPath("$.data.suggestedQuestions[0]").value("What should I do next?"));

        mockMvc.perform(post("/api/roadmap-steps/{stepId}/tutor/ask", stepId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Explain this step"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.context.scope").value("ROADMAP_STEP"))
                .andExpect(jsonPath("$.data.context.matchedStepId").value(stepId.toString()));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/tutor/ask", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void tutorBusinessErrorsAreMapped() throws Exception {
        when(aiTutorService.askWorkspace(eq(USER_ID), eq(workspaceId), any(TutorAskRequest.class)))
                .thenThrow(new AppException(ErrorCode.TUTOR_CONTEXT_NOT_READY));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/tutor/ask", workspaceId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What should I study?"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private StudySessionDetailResponse studySessionDetailResponse() {
        return StudySessionDetailResponse.builder()
                .session(inProgressSessionResponse())
                .task(calendarTaskResponse())
                .roadmapStep(StudySessionDetailResponse.RoadmapStepStudyResponse.builder()
                        .stepId(stepId)
                        .title("Practice Java basics")
                        .summary("Learn methods and classes")
                        .difficulty(DifficultyLevel.EASY)
                        .estimatedMinutes(45)
                        .sequenceNo(1)
                        .status(RoadmapStepStatus.CURRENT)
                        .whatToLearn(List.of("Methods", "Classes"))
                        .keyConcepts(List.of("Object"))
                        .learningOutcomes(List.of("Explain OOP"))
                        .recommendedFocus(List.of("Write examples"))
                        .build())
                .practice(StudySessionDetailResponse.PracticePromptResponse.builder()
                        .prompt("Explain object basics")
                        .expectedOutput("A short explanation")
                        .build())
                .resources(List.of())
                .actions(StudySessionDetailResponse.StudySessionActionsResponse.builder()
                        .canStart(false)
                        .canFinish(true)
                        .canCompleteTask(true)
                        .startEndpoint("/api/calendar/tasks/%s/sessions/start".formatted(taskId))
                        .finishEndpoint("/api/study-sessions/%s/finish".formatted(sessionId))
                        .pausePomodoroEndpoint("/api/study-sessions/%s/pomodoro/pause".formatted(sessionId))
                        .resumePomodoroEndpoint("/api/study-sessions/%s/pomodoro/resume".formatted(sessionId))
                        .nextPomodoroPhaseEndpoint("/api/study-sessions/%s/pomodoro/next-phase".formatted(sessionId))
                        .finishPomodoroEndpoint("/api/study-sessions/%s/pomodoro/finish".formatted(sessionId))
                        .build())
                .build();
    }

    private StudySessionResponse inProgressSessionResponse() {
        return baseSessionResponseBuilder(false)
                .status(StudySessionStatus.IN_PROGRESS)
                .pomodoro(pomodoro(PomodoroSessionStatus.IN_PROGRESS, PomodoroPhase.FOCUS))
                .build();
    }

    private StudySessionResponse pausedSessionResponse() {
        return baseSessionResponseBuilder(false)
                .status(StudySessionStatus.IN_PROGRESS)
                .pomodoro(pomodoro(PomodoroSessionStatus.PAUSED, PomodoroPhase.FOCUS))
                .build();
    }

    private StudySessionResponse shortBreakSessionResponse() {
        return baseSessionResponseBuilder(false)
                .status(StudySessionStatus.IN_PROGRESS)
                .pomodoro(pomodoro(PomodoroSessionStatus.IN_PROGRESS, PomodoroPhase.SHORT_BREAK))
                .build();
    }

    private StudySessionResponse completedSessionResponse(boolean taskCompleted) {
        return baseSessionResponseBuilder(taskCompleted)
                .status(StudySessionStatus.COMPLETED)
                .endedAt(Instant.parse("2026-06-23T12:45:00Z"))
                .durationMinutes(45)
                .notes("Finished practice")
                .focusScore(5)
                .pomodoro(pomodoro(PomodoroSessionStatus.COMPLETED, PomodoroPhase.FOCUS))
                .build();
    }

    private StudySessionResponse.StudySessionResponseBuilder baseSessionResponseBuilder(boolean taskCompleted) {
        return StudySessionResponse.builder()
                .sessionId(sessionId)
                .workspaceId(workspaceId)
                .calendarTaskId(taskId)
                .roadmapStepId(stepId)
                .startedAt(Instant.parse("2026-06-23T12:00:00Z"))
                .durationMinutes(25)
                .taskCompleted(taskCompleted)
                .minimumRequiredMinutes(15);
    }

    private StudySessionResponse.PomodoroTimerResponse pomodoro(
            PomodoroSessionStatus status,
            PomodoroPhase currentPhase
    ) {
        return StudySessionResponse.PomodoroTimerResponse.builder()
                .pomodoroId(UUID.randomUUID())
                .status(status)
                .currentPhase(currentPhase)
                .currentCycle(1)
                .totalCycles(4)
                .focusMinutes(25)
                .shortBreakMinutes(5)
                .longBreakMinutes(15)
                .remainingSeconds(1200)
                .startedAt(Instant.parse("2026-06-23T12:00:00Z"))
                .phaseStartedAt(Instant.parse("2026-06-23T12:00:00Z"))
                .phaseEndAt(Instant.parse("2026-06-23T12:25:00Z"))
                .completedFocusMinutes(status == PomodoroSessionStatus.COMPLETED ? 25 : 0)
                .build();
    }

    private CalendarTaskResponse calendarTaskResponse() {
        return CalendarTaskResponse.builder()
                .taskId(taskId)
                .workspaceId(workspaceId)
                .roadmapStepId(stepId)
                .title("Practice Java basics")
                .description("Finish hands-on practice")
                .taskDate(LocalDate.parse("2026-06-24"))
                .startTime(LocalTime.parse("19:00:00"))
                .endTime(LocalTime.parse("19:45:00"))
                .durationMinutes(45)
                .category(CalendarTaskCategory.PRACTICE)
                .priority(CalendarTaskPriority.HIGH)
                .status(CalendarTaskStatus.TODO)
                .source(CalendarTaskSource.AI_GENERATED)
                .xpReward(10)
                .overdue(false)
                .studySessionEndpoint("/api/calendar/tasks/%s/study-session".formatted(taskId))
                .build();
    }

    private QuizResponse quizResponse() {
        return QuizResponse.builder()
                .quizId(quizId)
                .stepId(stepId)
                .title("Java basics quiz")
                .passingScore(70)
                .questionCount(1)
                .status(QuizStatus.ACTIVE)
                .latestAttempt(attemptResponse())
                .questions(List.of(QuizQuestionResponse.builder()
                        .questionId(questionId)
                        .type(QuizQuestionType.SINGLE_CHOICE)
                        .question("What is a class?")
                        .sequenceNo(1)
                        .options(List.of(QuizOptionResponse.builder()
                                .optionId(optionId)
                                .label("A")
                                .text("A blueprint for objects")
                                .build()))
                        .build()))
                .build();
    }

    private QuizAttemptResponse attemptResponse() {
        return QuizAttemptResponse.builder()
                .attemptId(attemptId)
                .quizId(quizId)
                .score(100)
                .passed(true)
                .correctAnswers(1)
                .totalQuestions(1)
                .canCompleteStep(true)
                .feedback("Great job")
                .submittedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .results(List.of(QuizAnswerResultResponse.builder()
                        .questionId(questionId)
                        .selectedOptionId(optionId)
                        .correct(true)
                        .explanation("A class defines object structure")
                        .build()))
                .build();
    }

    private TutorAskResponse workspaceTutorResponse() {
        return TutorAskResponse.builder()
                .answer("Start with today's practice task, then review weak concepts.")
                .suggestedQuestions(List.of("What should I do next?"))
                .confidence("HIGH")
                .context(TutorContextResponse.builder()
                        .scope("WORKSPACE")
                        .workspaceId(workspaceId)
                        .workspaceName("Java Workspace")
                        .matchedStepId(stepId)
                        .matchedStepTitle("Practice Java basics")
                        .build())
                .build();
    }

    private TutorAskResponse stepTutorResponse() {
        return TutorAskResponse.builder()
                .answer("This step is about practicing Java objects.")
                .suggestedQuestions(List.of("Can you give me an example?"))
                .confidence("HIGH")
                .context(TutorContextResponse.builder()
                        .scope("ROADMAP_STEP")
                        .workspaceId(workspaceId)
                        .matchedStepId(stepId)
                        .matchedStepTitle("Practice Java basics")
                        .build())
                .build();
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("study-quiz-tutor-flow@example.com");
        user.setFullName("Study Quiz Tutor Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
