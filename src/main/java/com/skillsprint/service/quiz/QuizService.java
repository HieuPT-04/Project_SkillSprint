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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuizService {

    private static final int QUESTION_COUNT = 5;
    private static final int PASSING_SCORE = 70;

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

    @Transactional
    public QuizResponse generate(String userId, UUID stepId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.QUIZ_GENERATION);
        RoadmapStep step = findOwnedStep(userId, stepId);
        quotaService.validateCanAccessRoadmapStep(userId, step);

        boolean includeCorrectAnswers = subscriptionService.hasAdminDefaultPlan(userId);
        return quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                        stepId,
                        userId,
                        QuizStatus.ACTIVE
                )
                .map(quiz -> toQuizResponse(quiz, includeCorrectAnswers))
                .orElseGet(() -> createQuiz(step, includeCorrectAnswers));
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

    private QuizResponse createQuiz(RoadmapStep step, boolean includeCorrectAnswers) {
        List<MaterialChunk> chunks = materialChunkRepository
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(step.getWorkspace().getWorkspaceId());
        AiQuizDraft draft = geminiQuizClient.generate(step, chunks);
        List<AiQuizQuestionDraft> questionDrafts = normalizeDraft(draft);
        if (questionDrafts.isEmpty()) {
            // Gemini returned null / failed / timed out / quota exhausted, or its draft failed
            // validation. Do NOT save a low-quality fallback quiz with generic, meta, or
            // placeholder questions — fail in a controlled way so the client can show a
            // friendly retry message.
            log.warn("[AI] Quiz generation unavailable for step {}: no valid AI draft produced",
                    step.getStepId());
            throw new AppException(ErrorCode.QUIZ_GENERATION_UNAVAILABLE);
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
