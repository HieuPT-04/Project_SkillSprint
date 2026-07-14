package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceItemRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizOption;
import com.skillsprint.entity.QuizQuestion;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.quiz.QuizStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.QuizOptionRepository;
import com.skillsprint.repository.QuizQuestionRepository;
import com.skillsprint.repository.QuizRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import java.util.List;
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
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    StudyWorkspaceRepository workspaceRepository;
    RoadmapRepository roadmapRepository;
    RoadmapStepRepository roadmapStepRepository;
    QuizRepository quizRepository;
    QuizQuestionRepository quizQuestionRepository;
    QuizOptionRepository quizOptionRepository;
    ObjectMapper objectMapper;

    @Transactional
    public MarketplaceItemResponse createDraft(String userId, CreateMarketplaceItemRequest request) {
        StudyWorkspace workspace = workspaceRepository
                .findByWorkspaceIdAndUserUserIdAndStatusNot(request.getWorkspaceId(), userId,
                        com.skillsprint.enums.workspace.WorkspaceStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));

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
        snapshot.setChapterCount(steps.size());
        snapshot.setQuizCount(steps.size());
        snapshot.setQuestionCount(questionCount);
        snapshot.setContent(content);
        snapshotRepository.save(snapshot);

        return toResponse(item, snapshot);
    }

    private MarketplaceItemResponse toResponse(MarketplaceItem item, MarketplaceQuizPackSnapshot snapshot) {
        return MarketplaceItemResponse.builder()
                .itemId(item.getItemId())
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
                .reviewNote(item.getReviewNote())
                .createdAt(item.getCreatedAt())
                .publishedAt(item.getPublishedAt())
                .build();
    }
}
