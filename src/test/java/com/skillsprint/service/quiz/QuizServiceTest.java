package com.skillsprint.service.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
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
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
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
import com.skillsprint.service.quiz.ai.AiQuizDraft;
import com.skillsprint.service.quiz.ai.AiQuizGenerationException;
import com.skillsprint.service.quiz.ai.AiQuizGenerationFailureReason;
import com.skillsprint.service.quiz.ai.AiQuizGenerationInput;
import com.skillsprint.service.quiz.ai.AiQuizOptionDraft;
import com.skillsprint.service.quiz.ai.AiQuizQuestionDraft;
import com.skillsprint.service.quiz.ai.GeminiQuizClient;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

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

    RecordingTransactionManager transactionManager;
    QuizService quizService;
    User user;
    StudyWorkspace workspace;
    RoadmapStep step;

    /**
     * Real (if trivial) transaction manager so {@code TransactionSynchronizationManager}
     * reflects the actual transaction boundaries QuizService creates — letting tests
     * assert that Gemini runs outside a transaction and persistence inside one.
     */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        final AtomicInteger startedTransactions = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            startedTransactions.incrementAndGet();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
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
                subscriptionService,
                transactionManager
        );
        user = user("user-1");
        workspace = workspace(user);
        step = step(workspace);
    }

    @Test
    void generateReturnsControlledFailureAndDoesNotSaveQuizWhenAiDraftFailsNormalization() {
        stubNoActiveQuizWithChunks();
        // A draft that slipped past client-side validation but cannot be normalized
        // into 5 valid questions must never be saved as a fallback quiz.
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                .thenReturn(new AiQuizDraft(List.of(aiQuestion(1))));

        AppException exception = assertThrows(
                AppException.class,
                () -> quizService.generate("user-1", step.getStepId())
        );

        assertEquals(ErrorCode.QUIZ_GENERATION_UNAVAILABLE, exception.getErrorCode());
        verify(quizRepository, never()).save(any());
        verify(quizQuestionRepository, never()).save(any());
        verify(quizOptionRepository, never()).save(any());
        verify(quotaService).validateFeature("user-1", PlanFeatureKeys.QUIZ_GENERATION);
        verify(quotaService).validateCanAccessRoadmapStep("user-1", step);
    }

    @Test
    void generateSavesAiQuizAndHidesCorrectAnswersForLearnerWhenDraftIsValid() {
        List<QuizQuestion> savedQuestions = new ArrayList<>();
        List<QuizOption> savedOptions = new ArrayList<>();
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class))).thenReturn(aiDraft());
        stubQuizPersistence(savedQuestions, savedOptions);

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertEquals(5, response.getQuestionCount());
        assertEquals(5, response.getQuestions().size());
        assertEquals(20, savedOptions.size());
        assertTrue(savedQuestions.stream().allMatch(q -> q.getType() == QuizQuestionType.SINGLE_CHOICE));
        assertNull(response.getQuestions().get(0).getOptions().get(0).getCorrect());
        // Once in the prepare phase and once more in the write phase's revalidation.
        verify(quotaService, times(2)).validateFeature("user-1", PlanFeatureKeys.QUIZ_GENERATION);
        verify(quotaService, times(2)).validateCanAccessRoadmapStep("user-1", step);
    }

    @Test
    void generateRetriesAfterRateLimitAndCreatesExactlyOneQuiz() {
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                .thenThrow(new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.RATE_LIMITED, 429, Duration.ofSeconds(1)))
                .thenReturn(aiDraft());
        stubQuizPersistence(new ArrayList<>(), new ArrayList<>());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertEquals(5, response.getQuestionCount());
        verify(geminiQuizClient, times(2)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, times(1)).save(any(Quiz.class));
    }

    @Test
    void generateRetriesAfterUpstreamFailureAndCreatesExactlyOneQuiz() {
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                // Timeout / connection failure: no upstream status available.
                .thenThrow(new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.UPSTREAM_UNAVAILABLE))
                // Upstream 5xx.
                .thenThrow(new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.UPSTREAM_UNAVAILABLE, 503, null))
                .thenReturn(aiDraft());
        stubQuizPersistence(new ArrayList<>(), new ArrayList<>());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertEquals(5, response.getQuestionCount());
        verify(geminiQuizClient, times(3)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, times(1)).save(any(Quiz.class));
    }

    @Test
    void generateRetriesAfterInvalidDraftAndCreatesExactlyOneQuiz() {
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                .thenThrow(new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.INVALID_AI_DRAFT))
                .thenReturn(aiDraft());
        stubQuizPersistence(new ArrayList<>(), new ArrayList<>());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertEquals(5, response.getQuestionCount());
        verify(geminiQuizClient, times(2)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, times(1)).save(any(Quiz.class));
    }

    @Test
    void generateStopsAfterMaxAttemptsWhenRetryableFailurePersists() {
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                .thenThrow(new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.UPSTREAM_UNAVAILABLE, 503, null));

        AppException exception = assertThrows(
                AppException.class,
                () -> quizService.generate("user-1", step.getStepId())
        );

        assertEquals(ErrorCode.QUIZ_GENERATION_UNAVAILABLE, exception.getErrorCode());
        verify(geminiQuizClient, times(3)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void generateDoesNotRetryInvalidConfigurationFailures() {
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                .thenThrow(new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.INVALID_CONFIGURATION, 401, null));

        AppException exception = assertThrows(
                AppException.class,
                () -> quizService.generate("user-1", step.getStepId())
        );

        assertEquals(ErrorCode.QUIZ_GENERATION_UNAVAILABLE, exception.getErrorCode());
        verify(geminiQuizClient, times(1)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void generateDoesNotRetryWhenGeminiIsNotReady() {
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class)))
                .thenThrow(new AiQuizGenerationException(AiQuizGenerationFailureReason.NOT_READY));

        AppException exception = assertThrows(
                AppException.class,
                () -> quizService.generate("user-1", step.getStepId())
        );

        assertEquals(ErrorCode.QUIZ_GENERATION_UNAVAILABLE, exception.getErrorCode());
        verify(geminiQuizClient, times(1)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void generateRunsGeminiRetriesAndBackoffOutsideAnyTransaction() {
        List<Boolean> geminiSawActiveTransaction = new ArrayList<>();
        List<Boolean> saveSawActiveTransaction = new ArrayList<>();
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class))).thenAnswer(invocation -> {
            geminiSawActiveTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (geminiSawActiveTransaction.size() == 1) {
                throw new AiQuizGenerationException(
                        AiQuizGenerationFailureReason.UPSTREAM_UNAVAILABLE, 503, null);
            }
            return aiDraft();
        });
        stubQuizPersistence(new ArrayList<>(), new ArrayList<>(),
                () -> saveSawActiveTransaction.add(TransactionSynchronizationManager.isActualTransactionActive()));

        quizService.generate("user-1", step.getStepId());

        assertEquals(List.of(false, false), geminiSawActiveTransaction);
        assertTrue(saveSawActiveTransaction.stream().allMatch(Boolean::booleanValue));
        assertEquals(2, transactionManager.commits.get());
    }

    @Test
    void generatePersistsQuizQuestionsAndOptionsInOneWriteTransaction() {
        List<Integer> transactionIdAtEachSave = new ArrayList<>();
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class))).thenReturn(aiDraft());
        stubQuizPersistence(new ArrayList<>(), new ArrayList<>(),
                () -> transactionIdAtEachSave.add(transactionManager.startedTransactions.get()));

        quizService.generate("user-1", step.getStepId());

        // 1 quiz + 5 questions + 20 options, all inside the same (second) transaction.
        assertEquals(26, transactionIdAtEachSave.size());
        assertTrue(transactionIdAtEachSave.stream().allMatch(txId -> txId == 2));
        assertEquals(2, transactionManager.commits.get());
    }

    @Test
    void generateReturnsConcurrentlyCreatedQuizInsteadOfCreatingDuplicate() {
        Quiz concurrentlyCreated = quiz();
        QuizQuestion question = question(concurrentlyCreated, 1);
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));
        // No active quiz in the prepare phase, but one appears in the write-phase
        // re-check because another request finished while Gemini was generating.
        when(quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                step.getStepId(),
                "user-1",
                QuizStatus.ACTIVE
        )).thenReturn(Optional.empty()).thenReturn(Optional.of(concurrentlyCreated));
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId()))
                .thenReturn(List.of(chunk("Core Java summary")));
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class))).thenReturn(aiDraft());
        when(quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(concurrentlyCreated.getQuizId()))
                .thenReturn(List.of(question));
        when(quizOptionRepository.findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(
                concurrentlyCreated.getQuizId()))
                .thenReturn(List.of(option(question, "A", true, 1), option(question, "B", false, 2)));
        when(quizAttemptRepository.findFirstByQuizQuizIdAndUserUserIdOrderBySubmittedAtDesc(
                concurrentlyCreated.getQuizId(), "user-1"))
                .thenReturn(Optional.empty());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertEquals(concurrentlyCreated.getQuizId(), response.getQuizId());
        verify(quizRepository, never()).save(any());
        verify(quizQuestionRepository, never()).save(any());
        verify(quizOptionRepository, never()).save(any());
    }

    @Test
    void generateHidesCorrectAnswersWhenAdminPlanIsDowngradedDuringGeneration() {
        // The user is ADMIN_DEFAULT while Gemini generates, but is downgraded before
        // the write phase runs. The response must reflect the CURRENT entitlement
        // (queried inside the write transaction), never a stale prepare-phase value.
        AtomicBoolean downgraded = new AtomicBoolean(false);
        when(subscriptionService.hasAdminDefaultPlan("user-1")).thenAnswer(invocation -> !downgraded.get());
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class))).thenAnswer(invocation -> {
            downgraded.set(true);
            return aiDraft();
        });
        stubQuizPersistence(new ArrayList<>(), new ArrayList<>());

        QuizResponse response = quizService.generate("user-1", step.getStepId());

        assertTrue(response.getQuestions().stream()
                .flatMap(question -> question.getOptions().stream())
                .allMatch(option -> option.getCorrect() == null));
        // The write phase asked for the entitlement after the downgrade.
        verify(subscriptionService).hasAdminDefaultPlan("user-1");
    }

    @Test
    void generateFailsWithoutPersistingWhenQuizFeatureIsRevokedDuringGeneration() {
        // Prepare phase passes the feature check; the write phase re-check fails
        // because the plan was downgraded while Gemini was generating.
        doNothing()
                .doThrow(new AppException(ErrorCode.PREMIUM_FEATURE_REQUIRED))
                .when(quotaService).validateFeature("user-1", PlanFeatureKeys.QUIZ_GENERATION);
        stubNoActiveQuizWithChunks();
        when(geminiQuizClient.generate(any(AiQuizGenerationInput.class))).thenReturn(aiDraft());

        AppException exception = assertThrows(
                AppException.class,
                () -> quizService.generate("user-1", step.getStepId())
        );

        assertEquals(ErrorCode.PREMIUM_FEATURE_REQUIRED, exception.getErrorCode());
        verify(geminiQuizClient, times(1)).generate(any(AiQuizGenerationInput.class));
        verify(quizRepository, never()).save(any());
        verify(quizQuestionRepository, never()).save(any());
        verify(quizOptionRepository, never()).save(any());
    }

    @Test
    void generateRetriesThroughRealRestClientAfterUpstreamRateLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String draftJson = objectMapper.writeValueAsString(aiDraft());
        byte[] geminiSuccessBody = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", draftJson)))))
        )).getBytes(StandardCharsets.UTF_8);

        AtomicInteger upstreamCalls = new AtomicInteger();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            if (upstreamCalls.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(429, -1);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, geminiSuccessBody.length);
                exchange.getResponseBody().write(geminiSuccessBody);
            }
            exchange.close();
        });
        upstream.start();
        try {
            GeminiQuizClient realClient = new GeminiQuizClient(
                    new GeminiProperties(
                            true,
                            "test-key",
                            "gemini-test",
                            "http://127.0.0.1:" + upstream.getAddress().getPort(),
                            18000
                    ),
                    objectMapper,
                    RestClient.builder()
            );
            QuizService serviceWithRealClient = new QuizService(
                    roadmapStepRepository,
                    materialChunkRepository,
                    quizRepository,
                    quizQuestionRepository,
                    quizOptionRepository,
                    quizAttemptRepository,
                    quizAttemptAnswerRepository,
                    realClient,
                    quotaService,
                    pointService,
                    subscriptionService,
                    transactionManager
            );
            stubNoActiveQuizWithChunks();
            stubQuizPersistence(new ArrayList<>(), new ArrayList<>());

            QuizResponse response = serviceWithRealClient.generate("user-1", step.getStepId());

            assertEquals(5, response.getQuestionCount());
            assertEquals(2, upstreamCalls.get());
            verify(quizRepository, times(1)).save(any(Quiz.class));
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void generateIncludesCorrectAnswersForAdminDefaultPlan() {
        Quiz quiz = quiz();
        QuizQuestion question = question(quiz, 1);
        QuizOption correct = option(question, "A", true, 1);
        when(subscriptionService.hasAdminDefaultPlan("user-1")).thenReturn(true);
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

    private void stubNoActiveQuizWithChunks() {
        when(roadmapStepRepository.findById(step.getStepId())).thenReturn(Optional.of(step));
        when(quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                step.getStepId(),
                "user-1",
                QuizStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspace.getWorkspaceId()))
                .thenReturn(List.of(chunk("Core Java summary")));
    }

    private void stubQuizPersistence(List<QuizQuestion> savedQuestions, List<QuizOption> savedOptions) {
        stubQuizPersistence(savedQuestions, savedOptions, () -> {
        });
    }

    private void stubQuizPersistence(
            List<QuizQuestion> savedQuestions,
            List<QuizOption> savedOptions,
            Runnable onSave
    ) {
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            onSave.run();
            Quiz quiz = invocation.getArgument(0);
            quiz.setQuizId(UUID.randomUUID());
            return quiz;
        });
        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(invocation -> {
            onSave.run();
            QuizQuestion question = invocation.getArgument(0);
            question.setQuestionId(UUID.randomUUID());
            savedQuestions.add(question);
            return question;
        });
        when(quizOptionRepository.save(any(QuizOption.class))).thenAnswer(invocation -> {
            onSave.run();
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

    private AiQuizDraft aiDraft() {
        return new AiQuizDraft(List.of(
                aiQuestion(1),
                aiQuestion(2),
                aiQuestion(3),
                aiQuestion(4),
                aiQuestion(5)
        ));
    }

    private AiQuizQuestionDraft aiQuestion(int n) {
        return new AiQuizQuestionDraft(
                "SINGLE_CHOICE",
                "Câu hỏi nội dung số " + n + " kiểm tra kiến thức gì?",
                List.of(
                        new AiQuizOptionDraft("A", "Đáp án đúng " + n),
                        new AiQuizOptionDraft("B", "Đáp án sai B" + n),
                        new AiQuizOptionDraft("C", "Đáp án sai C" + n),
                        new AiQuizOptionDraft("D", "Đáp án sai D" + n)
                ),
                "A",
                "Giải thích cho câu " + n
        );
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
