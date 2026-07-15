package com.skillsprint.service.quiz;

import com.skillsprint.dto.request.quiz.SubmitQuizRequest;
import com.skillsprint.dto.response.quiz.QuizAnswerResultResponse;
import com.skillsprint.dto.response.quiz.QuizAttemptResponse;
import com.skillsprint.dto.response.quiz.QuizOptionResponse;
import com.skillsprint.dto.response.quiz.QuizQuestionResponse;
import com.skillsprint.dto.response.quiz.QuizResponse;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizAttempt;
import com.skillsprint.entity.QuizAttemptAnswer;
import com.skillsprint.entity.QuizOption;
import com.skillsprint.entity.QuizQuestion;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.enums.quiz.QuizAttemptStatus;
import com.skillsprint.enums.quiz.QuizQuestionType;
import com.skillsprint.enums.quiz.QuizStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.QuizAttemptAnswerRepository;
import com.skillsprint.repository.QuizAttemptRepository;
import com.skillsprint.repository.QuizOptionRepository;
import com.skillsprint.repository.QuizQuestionRepository;
import com.skillsprint.repository.QuizRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.service.quiz.ai.AiQuizDraft;
import com.skillsprint.service.quiz.ai.AiQuizGenerationException;
import com.skillsprint.service.quiz.ai.AiQuizGenerationInput;
import com.skillsprint.service.quiz.ai.AiQuizOptionDraft;
import com.skillsprint.service.quiz.ai.AiQuizQuestionDraft;
import com.skillsprint.service.quiz.ai.GeminiQuizClient;
import com.skillsprint.service.points.PointService;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuizService {

    private static final int QUESTION_COUNT = 5;
    private static final int PASSING_SCORE = 70;

    /** Total generation attempts against Gemini: the initial call plus 2 retries. */
    private static final int MAX_GENERATION_ATTEMPTS = 3;
    /** Exponential backoff before retry attempts 2 and 3. */
    private static final long[] RETRY_DELAYS_MS = {500L, 1_000L};
    /** Upper bound applied to an upstream Retry-After hint so a retry never stalls the request. */
    private static final long MAX_RETRY_AFTER_MS = 5_000L;

    RoadmapStepRepository roadmapStepRepository;
    MaterialChunkRepository materialChunkRepository;
    QuizRepository quizRepository;
    QuizQuestionRepository quizQuestionRepository;
    QuizOptionRepository quizOptionRepository;
    QuizAttemptRepository quizAttemptRepository;
    QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    GeminiQuizClient geminiQuizClient;
    QuotaService quotaService;
    PointService pointService;
    SubscriptionService subscriptionService;
    PlatformTransactionManager transactionManager;

    /**
     * Deliberately NOT {@code @Transactional}. Generation is split into three phases
     * so the slow external Gemini call (with its retries, timeouts, and backoff)
     * never holds a database transaction or pooled connection:
     * <ol>
     *   <li>short read-only transaction: validate access, return any existing active
     *       quiz, otherwise snapshot the prompt inputs into an immutable value object;</li>
     *   <li>no transaction: Gemini call with bounded retry/backoff;</li>
     *   <li>short write transaction: revalidate, re-check for a concurrently created
     *       active quiz, and persist quiz + questions + options atomically.</li>
     * </ol>
     * The phases run through {@link TransactionTemplate} (not self-invoked
     * {@code @Transactional} methods, which the Spring proxy would ignore).
     */
    public QuizResponse generate(String userId, UUID stepId) {
        PreparedQuizGeneration prepared = inReadOnlyTransaction(() -> prepareGeneration(userId, stepId));
        if (prepared.existingQuiz() != null) {
            return prepared.existingQuiz();
        }

        AiQuizDraft draft = generateDraftWithRetry(prepared.input());
        List<AiQuizQuestionDraft> questionDrafts = normalizeDraft(draft);
        if (questionDrafts.isEmpty()) {
            // The draft survived client-side validation but not normalization. Do NOT
            // save a low-quality fallback quiz with generic, meta, or placeholder
            // questions — fail in a controlled way so the client can show a friendly
            // retry message.
            log.warn("[AI] Quiz generation unavailable for step {}: no valid AI draft produced", stepId);
            throw new AppException(ErrorCode.QUIZ_GENERATION_UNAVAILABLE);
        }

        return inWriteTransaction(() -> saveGeneratedQuiz(userId, stepId, questionDrafts));
    }

    /**
     * Phase 1 (read-only transaction): access checks plus everything the
     * non-transactional Gemini phase will need, snapshotted into immutable values
     * before the transaction — and with it the ability to touch lazy JPA state — ends.
     */
    private PreparedQuizGeneration prepareGeneration(String userId, UUID stepId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.QUIZ_GENERATION);
        RoadmapStep step = findOwnedStep(userId, stepId);
        quotaService.validateCanAccessRoadmapStep(userId, step);

        Optional<Quiz> existing = quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                stepId,
                userId,
                QuizStatus.ACTIVE
        );
        if (existing.isPresent()) {
            // Entitlement is evaluated here only because the response is built and
            // returned immediately, inside this same transaction.
            return new PreparedQuizGeneration(
                    toQuizResponse(existing.get(), subscriptionService.hasAdminDefaultPlan(userId)), null);
        }

        List<MaterialChunk> chunks = materialChunkRepository
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(step.getWorkspace().getWorkspaceId());
        return new PreparedQuizGeneration(null, AiQuizGenerationInput.from(step, chunks));
    }

    /**
     * Phase 1 result: either an existing quiz to return as-is, or the Gemini prompt
     * snapshot. Deliberately carries NO plan-derived entitlement into the generation
     * path: Gemini can take tens of seconds, and the plan may change meanwhile, so
     * the write phase re-derives every entitlement itself.
     */
    private record PreparedQuizGeneration(
            QuizResponse existingQuiz,
            AiQuizGenerationInput input
    ) {
    }

    @Transactional(readOnly = true)
    public QuizResponse getCurrent(String userId, UUID stepId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.QUIZ_GENERATION);
        RoadmapStep step = findOwnedStep(userId, stepId);
        quotaService.validateCanAccessRoadmapStep(userId, step);

        Quiz quiz = quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                        stepId,
                        userId,
                        QuizStatus.ACTIVE
                )
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_NOT_FOUND));

        return toQuizResponse(quiz, subscriptionService.hasAdminDefaultPlan(userId));
    }

    @Transactional
    public QuizAttemptResponse submit(String userId, UUID quizId, SubmitQuizRequest request) {
        quotaService.validateFeature(userId, PlanFeatureKeys.QUIZ_GENERATION);
        Quiz quiz = findOwnedQuiz(userId, quizId);
        quotaService.validateCanAccessRoadmapStep(userId, quiz.getRoadmapStep());

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quizId);
        Map<UUID, QuizQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(QuizQuestion::getQuestionId, Function.identity()));

        Map<UUID, SubmitQuizRequest.AnswerRequest> answerByQuestionId = request.getAnswers().stream()
                .collect(Collectors.toMap(
                        SubmitQuizRequest.AnswerRequest::getQuestionId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        if (answerByQuestionId.size() != questions.size()) {
            throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Bạn cần trả lời đủ tất cả câu hỏi");
        }

        List<QuizOption> options = quizOptionRepository
                .findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(quizId);
        Map<UUID, QuizOption> optionById = options.stream()
                .collect(Collectors.toMap(QuizOption::getOptionId, Function.identity()));

        int correctCount = 0;
        List<QuizAnswerResultResponse> results = new ArrayList<>();
        List<QuizAttemptAnswer> attemptAnswers = new ArrayList<>();

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setUser(quiz.getUser());
        attempt.setQuestionCount(questions.size());
        attempt.setSubmittedAt(Instant.now());

        for (QuizQuestion question : questions) {
            SubmitQuizRequest.AnswerRequest answer = answerByQuestionId.get(question.getQuestionId());
            if (answer == null || !questionById.containsKey(answer.getQuestionId())) {
                throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER);
            }

            QuizOption selectedOption = optionById.get(answer.getSelectedOptionId());
            if (selectedOption == null
                    || !selectedOption.getQuestion().getQuestionId().equals(question.getQuestionId())) {
                throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER);
            }

            boolean correct = selectedOption.isCorrect();
            if (correct) {
                correctCount++;
            }

            QuizAttemptAnswer attemptAnswer = new QuizAttemptAnswer();
            attemptAnswer.setAttempt(attempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setSelectedOption(selectedOption);
            attemptAnswer.setCorrect(correct);
            attemptAnswers.add(attemptAnswer);

            results.add(QuizAnswerResultResponse.builder()
                    .questionId(question.getQuestionId())
                    .selectedOptionId(selectedOption.getOptionId())
                    .correct(correct)
                    .explanation(question.getExplanation())
                    .build());
        }

        int score = Math.round((correctCount * 100.0f) / questions.size());
        boolean passed = score >= quiz.getPassingScore();
        attempt.setCorrectCount(correctCount);
        attempt.setScore(score);
        attempt.setPassed(passed);
        attempt.setStatus(passed ? QuizAttemptStatus.PASSED : QuizAttemptStatus.FAILED);

        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);
        attemptAnswers.forEach(answer -> answer.setAttempt(savedAttempt));
        quizAttemptAnswerRepository.saveAll(attemptAnswers);
        try {
            pointService.awardQuizScore(quiz, savedAttempt);
        } catch (RuntimeException ex) {
            log.warn("[POINTS] Failed to award quiz XP for quiz {} attempt {}: {}",
                    quiz.getQuizId(),
                    savedAttempt.getAttemptId(),
                    ex.getMessage()
            );
        }

        return toAttemptResponse(savedAttempt, results);
    }

    @Transactional(readOnly = true)
    public QuizAttemptResponse getLatestAttempt(String userId, UUID quizId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.QUIZ_GENERATION);
        Quiz quiz = findOwnedQuiz(userId, quizId);
        QuizAttempt attempt = quizAttemptRepository
                .findFirstByQuizQuizIdAndUserUserIdOrderBySubmittedAtDesc(quiz.getQuizId(), userId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND));

        List<QuizAnswerResultResponse> results = quizAttemptAnswerRepository
                .findByAttemptAttemptId(attempt.getAttemptId())
                .stream()
                .map(answer -> QuizAnswerResultResponse.builder()
                        .questionId(answer.getQuestion().getQuestionId())
                        .selectedOptionId(answer.getSelectedOption().getOptionId())
                        .correct(answer.isCorrect())
                        .explanation(answer.getQuestion().getExplanation())
                        .build())
                .toList();

        return toAttemptResponse(attempt, results);
    }

    /**
     * Phase 3 (short write transaction): revalidate access, re-check that no other
     * request created an active quiz while Gemini was generating (returning that
     * quiz instead of a duplicate), then persist quiz + questions + options
     * atomically.
     *
     * <p>Every plan-derived entitlement is re-evaluated HERE, not carried over from
     * the prepare phase: the plan may have been downgraded while Gemini was
     * generating, and a stale snapshot would let a demoted user keep the feature or
     * see the correct-answer key.
     */
    private QuizResponse saveGeneratedQuiz(
            String userId,
            UUID stepId,
            List<AiQuizQuestionDraft> questionDrafts
    ) {
        quotaService.validateFeature(userId, PlanFeatureKeys.QUIZ_GENERATION);
        RoadmapStep step = findOwnedStep(userId, stepId);
        quotaService.validateCanAccessRoadmapStep(userId, step);
        boolean includeCorrectAnswers = subscriptionService.hasAdminDefaultPlan(userId);

        Optional<Quiz> concurrentlyCreated = quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                stepId,
                userId,
                QuizStatus.ACTIVE
        );
        if (concurrentlyCreated.isPresent()) {
            log.info("[AI] Active quiz for step {} was created concurrently; returning it instead of a duplicate",
                    stepId);
            return toQuizResponse(concurrentlyCreated.get(), includeCorrectAnswers);
        }

        Quiz quiz = new Quiz();
        quiz.setUser(step.getWorkspace().getUser());
        quiz.setWorkspace(step.getWorkspace());
        quiz.setRoadmapStep(step);
        quiz.setTitle("Quiz: " + step.getTitle());
        quiz.setPassingScore(PASSING_SCORE);
        quiz.setQuestionCount(questionDrafts.size());
        quiz.setStatus(QuizStatus.ACTIVE);
        Quiz savedQuiz = quizRepository.save(quiz);

        for (int i = 0; i < questionDrafts.size(); i++) {
            AiQuizQuestionDraft questionDraft = questionDrafts.get(i);
            QuizQuestion question = new QuizQuestion();
            question.setQuiz(savedQuiz);
            question.setType(parseQuestionType(questionDraft.type()));
            question.setQuestionText(clean(questionDraft.question(), "Câu hỏi quiz"));
            question.setExplanation(clean(questionDraft.explanation(), "Hãy xem lại nội dung liên quan trong bài học."));
            question.setSequenceNo(i + 1);
            QuizQuestion savedQuestion = quizQuestionRepository.save(question);

            List<AiQuizOptionDraft> options = questionDraft.options();
            for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                AiQuizOptionDraft optionDraft = options.get(optionIndex);
                QuizOption option = new QuizOption();
                option.setQuestion(savedQuestion);
                option.setLabel(normalizeLabel(optionDraft.label(), optionIndex));
                option.setOptionText(clean(optionDraft.text(), option.getLabel()));
                option.setCorrect(option.getLabel().equalsIgnoreCase(questionDraft.correctLabel()));
                option.setSequenceNo(optionIndex + 1);
                quizOptionRepository.save(option);
            }
        }

        return toQuizResponse(savedQuiz, includeCorrectAnswers);
    }

    /**
     * Phase 2 (no transaction): calls Gemini with a bounded retry — up to
     * {@link #MAX_GENERATION_ATTEMPTS} total attempts with exponential backoff,
     * retrying only transient failures (429, 5xx, timeouts, invalid drafts).
     * Configuration problems fail immediately. Whatever the reason, the
     * caller-facing failure stays {@code QUIZ_GENERATION_UNAVAILABLE} so the API
     * contract is unchanged; the classified reason is only logged.
     */
    private AiQuizDraft generateDraftWithRetry(AiQuizGenerationInput input) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                return geminiQuizClient.generate(input);
            } catch (AiQuizGenerationException ex) {
                // Safe by construction: reason + upstream status only, never prompts,
                // keys, material content, or generated question content.
                log.warn("[AI] Quiz generation attempt {}/{} failed for step {}: reason={}, upstreamStatus={}",
                        attempt,
                        MAX_GENERATION_ATTEMPTS,
                        input.stepId(),
                        ex.getReason(),
                        ex.getUpstreamStatus()
                );
                if (!ex.isRetryable() || attempt == MAX_GENERATION_ATTEMPTS) {
                    throw new AppException(ErrorCode.QUIZ_GENERATION_UNAVAILABLE);
                }
                sleepBeforeRetry(backoffMillis(attempt, ex));
            }
        }
        // Unreachable: the loop either returns or throws on the last attempt.
        throw new AppException(ErrorCode.QUIZ_GENERATION_UNAVAILABLE);
    }

    private long backoffMillis(int attempt, AiQuizGenerationException ex) {
        long delay = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length) - 1];
        if (ex.getRetryAfter() != null) {
            // Honor the upstream Retry-After hint when it asks for a longer wait,
            // but never beyond the safety cap.
            delay = Math.max(delay, Math.min(ex.getRetryAfter().toMillis(), MAX_RETRY_AFTER_MS));
        }
        return delay;
    }

    private void sleepBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.QUIZ_GENERATION_UNAVAILABLE);
        }
    }

    private <T> T inReadOnlyTransaction(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template.execute(status -> work.get());
    }

    private <T> T inWriteTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private List<AiQuizQuestionDraft> normalizeDraft(AiQuizDraft draft) {
        if (draft == null || draft.questions() == null || draft.questions().isEmpty()) {
            return List.of();
        }

        List<AiQuizQuestionDraft> validQuestions = new ArrayList<>();
        for (AiQuizQuestionDraft question : draft.questions()) {
            AiQuizQuestionDraft normalized = normalizeQuestion(question);
            if (normalized != null) {
                validQuestions.add(normalized);
            }
            if (validQuestions.size() == QUESTION_COUNT) {
                break;
            }
        }
        return validQuestions.size() == QUESTION_COUNT ? validQuestions : List.of();
    }

    private AiQuizQuestionDraft normalizeQuestion(AiQuizQuestionDraft question) {
        if (question == null || question.question() == null || question.question().isBlank()) {
            return null;
        }

        QuizQuestionType type = parseQuestionType(question.type());
        List<AiQuizOptionDraft> options = normalizeOptions(type, question.options());
        if (options.isEmpty()) {
            return null;
        }

        String correctLabel = normalizeLabel(question.correctLabel(), 0);
        boolean correctExists = options.stream().anyMatch(option -> option.label().equalsIgnoreCase(correctLabel));
        if (!correctExists) {
            return null;
        }

        return new AiQuizQuestionDraft(
                type.name(),
                question.question(),
                options,
                correctLabel,
                question.explanation()
        );
    }

    private List<AiQuizOptionDraft> normalizeOptions(QuizQuestionType type, List<AiQuizOptionDraft> rawOptions) {
        if (type == QuizQuestionType.TRUE_FALSE) {
            return List.of(
                    new AiQuizOptionDraft("A", "Đúng"),
                    new AiQuizOptionDraft("B", "Sai")
            );
        }
        if (rawOptions == null || rawOptions.size() < 4) {
            return List.of();
        }

        return rawOptions.stream()
                .limit(4)
                .map(option -> new AiQuizOptionDraft(
                        normalizeLabel(option.label(), rawOptions.indexOf(option)),
                        clean(option.text(), "Đáp án")
                ))
                .toList();
    }

    private QuizResponse toQuizResponse(Quiz quiz, boolean includeCorrectAnswers) {
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quiz.getQuizId());
        Map<UUID, List<QuizOption>> optionsByQuestionId = quizOptionRepository
                .findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(quiz.getQuizId())
                .stream()
                .collect(Collectors.groupingBy(
                        option -> option.getQuestion().getQuestionId(),
                        HashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::sortOptions)
                ));

        QuizAttemptResponse latestAttempt = quizAttemptRepository
                .findFirstByQuizQuizIdAndUserUserIdOrderBySubmittedAtDesc(
                        quiz.getQuizId(),
                        quiz.getUser().getUserId()
                )
                .map(attempt -> toAttemptResponse(attempt, null))
                .orElse(null);

        return QuizResponse.builder()
                .quizId(quiz.getQuizId())
                .stepId(quiz.getRoadmapStep().getStepId())
                .title(quiz.getTitle())
                .passingScore(quiz.getPassingScore())
                .questionCount(quiz.getQuestionCount())
                .status(quiz.getStatus())
                .latestAttempt(latestAttempt)
                .questions(questions.stream()
                        .map(question -> QuizQuestionResponse.builder()
                                .questionId(question.getQuestionId())
                                .type(question.getType())
                                .question(question.getQuestionText())
                                .sequenceNo(question.getSequenceNo())
                                .options(optionsByQuestionId
                                        .getOrDefault(question.getQuestionId(), List.of())
                                        .stream()
                                        .map(option -> QuizOptionResponse.builder()
                                                .optionId(option.getOptionId())
                                                .label(option.getLabel())
                                                .text(option.getOptionText())
                                                // Only admins get the answer key; null is
                                                // omitted from the JSON for everyone else.
                                                .correct(includeCorrectAnswers ? option.isCorrect() : null)
                                                .build())
                                        .toList())
                                .build())
                        .toList())
                .build();
    }

    private QuizAttemptResponse toAttemptResponse(QuizAttempt attempt, List<QuizAnswerResultResponse> results) {
        return QuizAttemptResponse.builder()
                .attemptId(attempt.getAttemptId())
                .quizId(attempt.getQuiz().getQuizId())
                .score(attempt.getScore())
                .passed(attempt.isPassed())
                .correctAnswers(attempt.getCorrectCount())
                .totalQuestions(attempt.getQuestionCount())
                .canCompleteStep(attempt.isPassed())
                .feedback(attempt.isPassed()
                        ? "Bạn đã đạt điểm qua quiz."
                        : "Bạn chưa đạt điểm qua quiz, hãy ôn lại bài và thử lại.")
                .submittedAt(attempt.getSubmittedAt())
                .results(results)
                .build();
    }

    private RoadmapStep findOwnedStep(String userId, UUID stepId) {
        return roadmapStepRepository.findById(stepId)
                .filter(step -> step.getWorkspace().getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.ROADMAP_NOT_FOUND));
    }

    private Quiz findOwnedQuiz(String userId, UUID quizId) {
        return quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_NOT_FOUND));
    }

    private List<QuizOption> sortOptions(List<QuizOption> options) {
        return options.stream()
                .sorted(Comparator.comparing(QuizOption::getSequenceNo))
                .toList();
    }

    private QuizQuestionType parseQuestionType(String value) {
        if (value == null || value.isBlank()) {
            return QuizQuestionType.SINGLE_CHOICE;
        }
        try {
            return QuizQuestionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return QuizQuestionType.SINGLE_CHOICE;
        }
    }

    private String normalizeLabel(String label, int index) {
        if (label == null || label.isBlank()) {
            return String.valueOf((char) ('A' + index));
        }
        return label.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

}
