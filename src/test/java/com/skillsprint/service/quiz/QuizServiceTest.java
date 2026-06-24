package com.skillsprint.service.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.quiz.SubmitQuizRequest;
import com.skillsprint.dto.response.quiz.QuizAttemptResponse;
import com.skillsprint.dto.response.quiz.QuizResponse;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizAttempt;
import com.skillsprint.entity.QuizAttemptAnswer;
import com.skillsprint.entity.QuizOption;
import com.skillsprint.entity.QuizQuestion;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.quiz.QuizAttemptStatus;
import com.skillsprint.enums.quiz.QuizQuestionType;
import com.skillsprint.enums.quiz.QuizStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.QuizAttemptAnswerRepository;
import com.skillsprint.repository.QuizAttemptRepository;
import com.skillsprint.repository.QuizOptionRepository;
import com.skillsprint.repository.QuizQuestionRepository;
import com.skillsprint.repository.QuizRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.quiz.ai.GeminiQuizClient;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.util.ArrayList;
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
class QuizServiceTest {

    @Mock
    RoadmapStepRepository roadmapStepRepository;

    @Mock
    MaterialChunkRepository materialChunkRepository;

    @Mock
    QuizRepository quizRepository;

    @Mock
    QuizQuestionRepository quizQuestionRepository;

    @Mock
    QuizOptionRepository quizOptionRepository;

    @Mock
    QuizAttemptRepository quizAttemptRepository;

    @Mock
    QuizAttemptAnswerRepository quizAttemptAnswerRepository;

    @Mock
    GeminiQuizClient geminiQuizClient;

    @Mock
    QuotaService quotaService;

    @Mock
    PointService pointService;

    @Mock
    SubscriptionService subscriptionService;

    QuizService quizService;
    User user;
    StudyWorkspace workspace;
    RoadmapStep step;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(
                roadmapStepRepository,
                materialChunkRepository,
                quizRepository,
                quizQuestionRepository,
                quizOptionRepository,
                quizAttemptRepository,
                quizAttemptAnswerRepository,
                geminiQuizClient,
                quotaService,
                pointService,
                subscriptionService
        );
        user = user("user-1");
        workspace = workspace(user);
        step = step(workspace);
    }

    @Test
    void generateBuildsFallbackQuizWhenAiDraftIsInvalidAndHidesCorrectAnswersForLearner() {
        List<QuizQuestion> savedQuestions = new ArrayList<>();
        List<QuizOption> savedOptions = new ArrayList<>();
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));
        when(quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                step.getStepId(),
                "user-1",
                QuizStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId()))
                .thenReturn(List.of(chunk("Core Java summary")));
        when(geminiQuizClient.generate(any(RoadmapStep.class), anyList())).thenReturn(null);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            quiz.setQuizId(UUID.randomUUID());
            return quiz;
        });
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(invocation -> {
            QuizQuestion question = invocation.getArgument(0);
            question.setQuestionId(UUID.randomUUID());
            savedQuestions.add(question);
            return question;
        });
        when(quizOptionRepository.save(any(QuizOption.class))).thenAnswer(invocation -> {
            QuizOption option = invocation.getArgument(0);
            option.setOptionId(UUID.randomUUID());
            savedOptions.add(option);
            return option;
        });
        when(quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(any(UUID.class)))
                .thenAnswer(invocation -> savedQuestions);
        when(quizOptionRepository.findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(any(UUID.class)))
                .thenAnswer(invocation -> savedOptions);
        when(quizAttemptRepository.findFirstByQuizQuizIdAndUserUserIdOrderBySubmittedAtDesc(any(UUID.class), any()))
                .thenReturn(Optional.empty());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertEquals(5, response.getQuestionCount());
        assertEquals(5, response.getQuestions().size());
        assertEquals(16, savedOptions.size());
        assertNull(response.getQuestions().get(0).getOptions().get(0).getCorrect());
        verify(quotaService).validateFeature("user-1", PlanFeatureKeys.QUIZ_GENERATION);
        verify(quotaService).validateCanAccessRoadmapStep("user-1", step);
    }

    @Test
    void generateIncludesCorrectAnswersForAdminDefaultPlan() {
        Quiz quiz = quiz();
        QuizQuestion question = question(quiz, 1);
        QuizOption correct = option(question, "A", true, 1);
        ServicePlan plan = new ServicePlan();
        plan.setPlanType(ServicePlanType.ADMIN_DEFAULT);
        when(subscriptionService.getCurrentPlan("user-1")).thenReturn(plan);
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));
        when(quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                step.getStepId(),
                "user-1",
                QuizStatus.ACTIVE
        )).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quiz.getQuizId())).thenReturn(List.of(question));
        when(quizOptionRepository.findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(quiz.getQuizId()))
                .thenReturn(List.of(correct, option(question, "B", false, 2)));
        when(quizAttemptRepository.findFirstByQuizQuizIdAndUserUserIdOrderBySubmittedAtDesc(quiz.getQuizId(), "user-1"))
                .thenReturn(Optional.empty());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertTrue(response.getQuestions().get(0).getOptions().get(0).getCorrect());
        assertFalse(response.getQuestions().get(0).getOptions().get(1).getCorrect());
    }

    @Test
    void submitScoresAttemptPersistsAnswersAndAwardsPoints() {
        Quiz quiz = quiz();
        QuizQuestion first = question(quiz, 1);
        QuizQuestion second = question(quiz, 2);
        QuizOption firstCorrect = option(first, "A", true, 1);
        QuizOption secondCorrect = option(second, "A", true, 1);
        when(quizRepository.findByQuizIdAndUserUserId(quiz.getQuizId(), "user-1")).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quiz.getQuizId()))
                .thenReturn(List.of(first, second));
        when(quizOptionRepository.findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(quiz.getQuizId()))
                .thenReturn(List.of(
                        firstCorrect,
                        option(first, "B", false, 2),
                        secondCorrect,
                        option(second, "B", false, 2)
                ));
        when(quizAttemptRepository.save(any(QuizAttempt.class))).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(UUID.randomUUID());
            return attempt;
        });

        QuizAttemptResponse response = quizService.submit(
                "user-1",
                quiz.getQuizId(),
                submitRequest(firstCorrect, secondCorrect)
        );

        assertEquals(100, response.getScore());
        assertTrue(response.isPassed());
        assertEquals(2, response.getCorrectAnswers());
        assertEquals(QuizAttemptStatus.PASSED, captureAttempt().getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuizAttemptAnswer>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(quizAttemptAnswerRepository).saveAll(answersCaptor.capture());
        assertEquals(2, answersCaptor.getValue().size());
        assertTrue(answersCaptor.getValue().stream().allMatch(QuizAttemptAnswer::isCorrect));
        verify(pointService).awardQuizScore(any(Quiz.class), any(QuizAttempt.class));
    }

    @Test
    void submitRejectsMissingAnswersBeforeSavingAttempt() {
        Quiz quiz = quiz();
        QuizQuestion first = question(quiz, 1);
        QuizQuestion second = question(quiz, 2);
        QuizOption firstCorrect = option(first, "A", true, 1);
        when(quizRepository.findByQuizIdAndUserUserId(quiz.getQuizId(), "user-1")).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quiz.getQuizId()))
                .thenReturn(List.of(first, second));

        AppException exception = assertThrows(
                AppException.class,
                () -> quizService.submit("user-1", quiz.getQuizId(), submitRequest(firstCorrect))
        );

        assertEquals(ErrorCode.QUIZ_INVALID_ANSWER, exception.getErrorCode());
        verify(quizAttemptRepository, never()).save(any());
        verify(quizAttemptAnswerRepository, never()).saveAll(anyList());
    }

    private QuizAttempt captureAttempt() {
        ArgumentCaptor<QuizAttempt> captor = ArgumentCaptor.forClass(QuizAttempt.class);
        verify(quizAttemptRepository).save(captor.capture());
        return captor.getValue();
    }

    private SubmitQuizRequest submitRequest(QuizOption... options) {
        SubmitQuizRequest request = new SubmitQuizRequest();
        request.setAnswers(java.util.Arrays.stream(options).map(option -> {
            SubmitQuizRequest.AnswerRequest answer = new SubmitQuizRequest.AnswerRequest();
            answer.setQuestionId(option.getQuestion().getQuestionId());
            answer.setSelectedOptionId(option.getOptionId());
            return answer;
        }).toList());
        return request;
    }

    private Quiz quiz() {
        Quiz quiz = new Quiz();
        quiz.setQuizId(UUID.randomUUID());
        quiz.setUser(user);
        quiz.setWorkspace(workspace);
        quiz.setRoadmapStep(step);
        quiz.setTitle("Quiz");
        quiz.setPassingScore(70);
        quiz.setQuestionCount(2);
        quiz.setStatus(QuizStatus.ACTIVE);
        return quiz;
    }

    private QuizQuestion question(Quiz quiz, int sequenceNo) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestionId(UUID.randomUUID());
        question.setQuiz(quiz);
        question.setType(QuizQuestionType.SINGLE_CHOICE);
        question.setQuestionText("Question " + sequenceNo);
        question.setExplanation("Explanation " + sequenceNo);
        question.setSequenceNo(sequenceNo);
        return question;
    }

    private QuizOption option(QuizQuestion question, String label, boolean correct, int sequenceNo) {
        QuizOption option = new QuizOption();
        option.setOptionId(UUID.randomUUID());
        option.setQuestion(question);
        option.setLabel(label);
        option.setOptionText("Option " + label);
        option.setCorrect(correct);
        option.setSequenceNo(sequenceNo);
        return option;
    }

    private MaterialChunk chunk(String content) {
        MaterialChunk chunk = new MaterialChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setWorkspace(workspace);
        chunk.setContent(content);
        chunk.setChunkIndex(1);
        return chunk;
    }

    private RoadmapStep step(StudyWorkspace workspace) {
        RoadmapStep step = new RoadmapStep();
        step.setStepId(UUID.randomUUID());
        step.setWorkspace(workspace);
        step.setTitle("Java");
        step.setSummary("Learn Java");
        step.setKeyConcepts(List.of("JVM", "Class"));
        step.setSequenceNo(1);
        return step;
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
