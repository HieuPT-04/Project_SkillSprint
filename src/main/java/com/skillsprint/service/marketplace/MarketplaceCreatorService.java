package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceItemRequest;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceQuizRequest;
import com.skillsprint.dto.response.marketplace.CreatorValidationPackResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceQuizAttemptResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQuizAttempt;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizOption;
import com.skillsprint.entity.QuizQuestion;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplaceQuizAttemptType;
import com.skillsprint.enums.quiz.QuizStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizAttemptRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.QuizOptionRepository;
import com.skillsprint.repository.QuizQuestionRepository;
import com.skillsprint.repository.QuizRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.service.subscription.SubscriptionService;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCreatorService {

    static final int MINIMUM_QUIZ_COUNT = 4;
    static final int MINIMUM_QUESTION_COUNT = 20;

    MarketplaceItemRepository marketplaceItemRepository;
    MarketplaceQuizAttemptRepository marketplaceQuizAttemptRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    StudyWorkspaceRepository workspaceRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    QuizRepository quizRepository;
    QuizQuestionRepository quizQuestionRepository;
    QuizOptionRepository quizOptionRepository;
    SubscriptionService subscriptionService;
    MarketplacePackVersionService packVersionService;
    MarketplaceQualityService qualityService;
    ObjectMapper objectMapper;

    @Transactional
    public MarketplaceItemResponse createDraft(String userId, CreateMarketplaceItemRequest request) {
        StudyWorkspace workspace = workspaceRepository
                .findByWorkspaceIdAndUserUserIdAndStatusNot(request.getWorkspaceId(), userId,
                        com.skillsprint.enums.workspace.WorkspaceStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));

        PackContent pack = buildPackContent(userId, workspace);

        MarketplaceItem item = new MarketplaceItem();
        item.setCreator(workspace.getUser());
        item.setSourceWorkspace(workspace);
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription());
        item.setSubject(request.getSubject().trim());
        item.setPriceCoins(request.getPriceCoins());
        item.setStatus(MarketplaceItemStatus.DRAFT);
        item = marketplaceItemRepository.save(item);

        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setItem(item);
        snapshot.setChapterCount(pack.chapterCount());
        snapshot.setQuizCount(pack.quizCount());
        snapshot.setQuestionCount(pack.questionCount());
        snapshot.setContent(pack.content());
        snapshotRepository.save(snapshot);

        MarketplacePackVersion version = packVersionService.createInitialVersion(item, snapshot);
        qualityService.queue(version);

        return toResponse(
                item,
                snapshot,
                MarketplacePackVersionIdentity.of(version),
                qualityService.summary(version)
        );
    }

    @Transactional(readOnly = true)
    public CreatorValidationPackResponse getCreatorValidationPack(String userId, UUID itemId) {
        MarketplaceItem item = marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        if (item.getStatus() != MarketplaceItemStatus.DRAFT) {
            throw new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        }
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        boolean includeCorrectAnswers = subscriptionService.hasAdminDefaultPlan(userId);

        List<CreatorValidationPackResponse.ChapterResponse> chapters = new ArrayList<>();
        for (JsonNode chapterNode : snapshot.getContent().path("chapters")) {
            List<CreatorValidationPackResponse.QuestionResponse> questions = new ArrayList<>();
            for (JsonNode questionNode : chapterNode.path("quiz").path("questions")) {
                List<CreatorValidationPackResponse.OptionResponse> options = new ArrayList<>();
                for (JsonNode optionNode : questionNode.path("options")) {
                    options.add(CreatorValidationPackResponse.OptionResponse.builder()
                            .optionId(UUID.fromString(optionNode.path("optionId").asText()))
                            .label(optionNode.path("label").asText())
                            .text(optionNode.path("text").asText())
                            .sequenceNo(optionNode.path("sequenceNo").asInt())
                            .correct(includeCorrectAnswers ? optionNode.path("correct").asBoolean(false) : null)
                            .build());
                }
                questions.add(CreatorValidationPackResponse.QuestionResponse.builder()
                        .questionId(UUID.fromString(questionNode.path("questionId").asText()))
                        .type(questionNode.path("type").asText())
                        .text(questionNode.path("text").asText())
                        .sequenceNo(questionNode.path("sequenceNo").asInt())
                        .options(options)
                        .build());
            }
            chapters.add(CreatorValidationPackResponse.ChapterResponse.builder()
                    .sequenceNo(chapterNode.path("sequenceNo").asInt())
                    .title(chapterNode.path("title").asText())
                    .summary(chapterNode.path("summary").isNull() ? null : chapterNode.path("summary").asText())
                    .quizTitle(chapterNode.path("quiz").path("title").asText())
                    .questions(questions)
                    .build());
        }

        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        return CreatorValidationPackResponse.builder()
                .itemId(item.getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .sourceWorkspaceId(item.getSourceWorkspace().getWorkspaceId())
                .title(item.getTitle())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .creatorValidationScore(item.getCreatorValidationScore())
                .chapters(chapters)
                .build();
    }

    @Transactional
    public MarketplaceItemResponse refreshSnapshot(String userId, UUID itemId) {
        MarketplaceItem item = marketplaceItemRepository.findByItemIdAndCreatorUserIdForUpdate(itemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        if (item.getStatus() != MarketplaceItemStatus.DRAFT) {
            throw new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        }
        StudyWorkspace workspace = workspaceRepository
                .findByWorkspaceIdAndUserUserIdAndStatusNot(item.getSourceWorkspace().getWorkspaceId(), userId,
                        com.skillsprint.enums.workspace.WorkspaceStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));

        PackContent pack = buildPackContent(userId, workspace);

        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        snapshot.setChapterCount(pack.chapterCount());
        snapshot.setQuizCount(pack.quizCount());
        snapshot.setQuestionCount(pack.questionCount());
        snapshot.setContent(pack.content());
        snapshotRepository.save(snapshot);

        item.setCreatorValidationScore(null);
        item = marketplaceItemRepository.save(item);
        MarketplacePackVersion version = packVersionService.syncFromLegacyItem(item, snapshot).orElse(null);
        if (version != null) {
            qualityService.queue(version);
        }
        return toResponse(
                item,
                snapshot,
                MarketplacePackVersionIdentity.ofNullable(version),
                version == null ? MarketplaceQualityService.Summary.EMPTY : qualityService.summary(version)
        );
    }

    private PackContent buildPackContent(String userId, StudyWorkspace workspace) {
        Roadmap roadmap = roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspace.getWorkspaceId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_WORKSPACE_NOT_ELIGIBLE));
        List<RoadmapStep> steps = roadmapStepRepository.findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId());
        if (steps.size() < MINIMUM_QUIZ_COUNT) {
            throw new AppException(ErrorCode.MARKETPLACE_WORKSPACE_NOT_ELIGIBLE,
                    "Workspace cần tối thiểu " + MINIMUM_QUIZ_COUNT + " roadmap step có quiz");
        }

        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        int questionCount = 0;

        for (RoadmapStep step : steps) {
            Quiz quiz = quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                            step.getStepId(), userId, QuizStatus.ACTIVE)
                    .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_WORKSPACE_NOT_ELIGIBLE,
                            "Roadmap step chưa có quiz: " + step.getTitle()));
            List<QuizQuestion> questions = quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quiz.getQuizId());
            if (questions.isEmpty()) {
                throw new AppException(ErrorCode.MARKETPLACE_WORKSPACE_NOT_ELIGIBLE,
                        "Quiz chưa có câu hỏi: " + quiz.getTitle());
            }

            ObjectNode chapter = chapters.addObject();
            chapter.put("sequenceNo", step.getSequenceNo());
            chapter.put("title", step.getTitle());
            chapter.put("summary", step.getSummary());
            ObjectNode quizNode = chapter.putObject("quiz");
            quizNode.put("title", quiz.getTitle());
            ArrayNode questionNodes = quizNode.putArray("questions");

            for (QuizQuestion question : questions) {
                ObjectNode questionNode = questionNodes.addObject();
                questionNode.put("questionId", question.getQuestionId().toString());
                questionNode.put("type", question.getType().name());
                questionNode.put("text", question.getQuestionText());
                questionNode.put("explanation", question.getExplanation());
                questionNode.put("sequenceNo", question.getSequenceNo());
                ObjectNode evidence = questionNode.putObject("evidence");
                evidence.put("sourceStepId", step.getStepId().toString());
                evidence.put("explanation", question.getExplanation());
                ArrayNode sourceChunkIds = evidence.putArray("sourceChunkIds");
                evidenceSourceChunkIds(step).forEach(sourceChunkIds::add);
                ArrayNode options = questionNode.putArray("options");
                for (QuizOption option : quizOptionRepository.findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(quiz.getQuizId())
                        .stream().filter(value -> value.getQuestion().getQuestionId().equals(question.getQuestionId())).toList()) {
                    ObjectNode optionNode = options.addObject();
                    optionNode.put("optionId", option.getOptionId().toString());
                    optionNode.put("label", option.getLabel());
                    optionNode.put("text", option.getOptionText());
                    optionNode.put("correct", option.isCorrect());
                    optionNode.put("sequenceNo", option.getSequenceNo());
                }
                questionCount++;
            }
        }

        if (questionCount < MINIMUM_QUESTION_COUNT) {
            throw new AppException(ErrorCode.MARKETPLACE_WORKSPACE_NOT_ELIGIBLE,
                    "Quiz Pack cần tối thiểu " + MINIMUM_QUESTION_COUNT + " câu hỏi");
        }

        return new PackContent(content, steps.size(), steps.size(), questionCount);
    }

    private record PackContent(ObjectNode content, int chapterCount, int quizCount, int questionCount) {
    }

    private List<String> evidenceSourceChunkIds(RoadmapStep step) {
        Set<String> sourceChunkIds = new LinkedHashSet<>();
        if (step.getTopic() != null && step.getTopic().getSourceChunkIds() != null) {
            sourceChunkIds.addAll(step.getTopic().getSourceChunkIds());
        }
        if (step.getChapter() != null && step.getChapter().getSourceChunkIds() != null) {
            sourceChunkIds.addAll(step.getChapter().getSourceChunkIds());
        }
        return List.copyOf(sourceChunkIds);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> getMyItems(String userId) {
        List<MarketplaceItem> items = marketplaceItemRepository.findByCreatorUserIdOrderByCreatedAtDesc(userId);
        List<UUID> itemIds = items.stream().map(MarketplaceItem::getItemId).toList();
        Map<UUID, MarketplacePackVersionIdentity> identities =
                packVersionService.identitiesOf(itemIds);
        Map<UUID, MarketplaceQualityService.Summary> qualitySummaries =
                qualityService.summariesByLegacyItemIds(itemIds);
        return items.stream()
                .map(item -> toResponse(
                        item,
                        snapshotRepository.findByItemItemId(item.getItemId())
                                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND)),
                        identities.getOrDefault(item.getItemId(), MarketplacePackVersionIdentity.EMPTY),
                        qualitySummaries.getOrDefault(item.getItemId(), MarketplaceQualityService.Summary.EMPTY)))
                .toList();
    }

    @Transactional
    public MarketplaceQuizAttemptResponse validateFullPack(
            String userId,
            UUID itemId,
            SubmitMarketplaceQuizRequest request
    ) {
        MarketplaceItem item = marketplaceItemRepository.findByItemIdAndCreatorUserIdForUpdate(itemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        if (item.getStatus() != MarketplaceItemStatus.DRAFT) {
            throw new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        }

        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        Map<UUID, UUID> correctOptions = correctOptions(snapshot);
        Map<UUID, UUID> submittedAnswers = new HashMap<>();
        for (SubmitMarketplaceQuizRequest.AnswerRequest answer : request.getAnswers()) {
            if (submittedAnswers.put(answer.getQuestionId(), answer.getSelectedOptionId()) != null) {
                throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Không được gửi trùng đáp án cho một câu hỏi");
            }
        }
        if (!submittedAnswers.keySet().equals(correctOptions.keySet())) {
            throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Cần trả lời toàn bộ câu hỏi trong Quiz Pack");
        }

        int correctCount = 0;
        for (Map.Entry<UUID, UUID> answer : submittedAnswers.entrySet()) {
            if (answer.getValue().equals(correctOptions.get(answer.getKey()))) {
                correctCount++;
            }
        }
        int questionCount = correctOptions.size();
        int score = (int) Math.round(correctCount * 100.0 / questionCount);

        MarketplaceQuizAttempt attempt = new MarketplaceQuizAttempt();
        attempt.setItem(item);
        attempt.setPackVersion(packVersionService.findByItemId(itemId).orElse(null));
        attempt.setUser(item.getCreator());
        attempt.setAttemptType(MarketplaceQuizAttemptType.CREATOR_VALIDATION);
        attempt.setScore(score);
        attempt.setCorrectCount(correctCount);
        attempt.setQuestionCount(questionCount);
        attempt.setDurationSeconds(request.getDurationSeconds());
        attempt.setSuspicious(false);
        attempt.setCompletedAt(java.time.Instant.now());
        attempt = marketplaceQuizAttemptRepository.save(attempt);

        item.setCreatorValidationScore(score);
        marketplaceItemRepository.save(item);
        return toAttemptResponse(attempt, syncedIdentity(item, snapshot));
    }

    @Transactional
    public MarketplaceItemResponse submitForReview(String userId, UUID itemId) {
        MarketplaceItem item = marketplaceItemRepository.findByItemIdAndCreatorUserIdForUpdate(itemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        if (item.getStatus() != MarketplaceItemStatus.DRAFT) {
            throw new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        }
        if (item.getCreatorValidationScore() == null || item.getCreatorValidationScore() < 90) {
            throw new AppException(ErrorCode.MARKETPLACE_CREATOR_VALIDATION_REQUIRED);
        }
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        MarketplacePackVersion syncedVersion = packVersionService.syncFromLegacyItem(item, snapshot)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        qualityService.requireCurrentPass(syncedVersion);

        item.setStatus(MarketplaceItemStatus.PENDING_REVIEW);
        item = marketplaceItemRepository.save(item);
        syncedVersion = packVersionService.syncFromLegacyItem(item, snapshot).orElse(syncedVersion);
        return toResponse(
                item,
                snapshot,
                MarketplacePackVersionIdentity.of(syncedVersion),
                qualityService.summary(syncedVersion)
        );
    }

    private MarketplacePackVersionIdentity syncedIdentity(MarketplaceItem item, MarketplaceQuizPackSnapshot snapshot) {
        return MarketplacePackVersionIdentity.ofNullable(
                packVersionService.syncFromLegacyItem(item, snapshot).orElse(null));
    }

    private Map<UUID, UUID> correctOptions(MarketplaceQuizPackSnapshot snapshot) {
        Map<UUID, UUID> correctOptions = new HashMap<>();
        Set<UUID> seenQuestions = new HashSet<>();
        snapshot.getContent().path("chapters").forEach(chapter -> chapter.path("quiz").path("questions").forEach(question -> {
            UUID questionId = UUID.fromString(question.path("questionId").asText());
            if (!seenQuestions.add(questionId)) {
                throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Snapshot Quiz Pack không hợp lệ");
            }
            UUID correctOptionId = null;
            for (com.fasterxml.jackson.databind.JsonNode option : question.path("options")) {
                if (option.path("correct").asBoolean(false)) {
                    if (correctOptionId != null) {
                        throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Snapshot có nhiều đáp án đúng");
                    }
                    correctOptionId = UUID.fromString(option.path("optionId").asText());
                }
            }
            if (correctOptionId == null) {
                throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Snapshot thiếu đáp án đúng");
            }
            correctOptions.put(questionId, correctOptionId);
        }));
        return correctOptions;
    }

    private MarketplaceQuizAttemptResponse toAttemptResponse(
            MarketplaceQuizAttempt attempt,
            MarketplacePackVersionIdentity identity
    ) {
        return MarketplaceQuizAttemptResponse.builder()
                .attemptId(attempt.getAttemptId())
                .itemId(attempt.getItem().getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .questionCount(attempt.getQuestionCount())
                .durationSeconds(attempt.getDurationSeconds())
                .completedAt(attempt.getCompletedAt())
                .build();
    }

    private MarketplaceItemResponse toResponse(
            MarketplaceItem item,
            MarketplaceQuizPackSnapshot snapshot,
            MarketplacePackVersionIdentity identity,
            MarketplaceQualityService.Summary qualitySummary
    ) {
        return MarketplaceItemResponse.builder()
                .itemId(item.getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .sourceWorkspaceId(item.getSourceWorkspace().getWorkspaceId())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .priceCoins(item.getPriceCoins())
                .status(item.getStatus())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .creatorValidationScore(item.getCreatorValidationScore())
                .qualityStatus(qualitySummary.status())
                .qualityScore(qualitySummary.score())
                .qualityCurrent(qualitySummary.current())
                .reviewNote(item.getReviewNote())
                .createdAt(item.getCreatedAt())
                .publishedAt(item.getPublishedAt())
                .build();
    }
}
