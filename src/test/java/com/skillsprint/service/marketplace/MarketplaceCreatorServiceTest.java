package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.CreatorValidationPackResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizOption;
import com.skillsprint.entity.QuizQuestion;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.quiz.QuizQuestionType;
import com.skillsprint.enums.quiz.QuizStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceCreatorServiceTest {

    @Mock MarketplaceItemRepository marketplaceItemRepository;
    @Mock MarketplaceQuizAttemptRepository marketplaceQuizAttemptRepository;
    @Mock MarketplaceQuizPackSnapshotRepository snapshotRepository;
    @Mock StudyWorkspaceRepository workspaceRepository;
    @Mock RoadmapRepository roadmapRepository;
    @Mock RoadmapStepRepository roadmapStepRepository;
    @Mock QuizRepository quizRepository;
    @Mock QuizQuestionRepository quizQuestionRepository;
    @Mock QuizOptionRepository quizOptionRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock MarketplacePackVersionService packVersionService;
    @Mock MarketplaceQualityService qualityService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks MarketplaceCreatorService service;

    @BeforeEach
    void stubVersionIdentity() {
        // These tests predate the pack/version foundation and assert legacy item
        // behavior only; the additive identity fields have their own coverage in
        // MarketplacePackVersionCompatibilityTest.
        lenient().when(packVersionService.identityOf(any()))
                .thenReturn(MarketplacePackVersionIdentity.EMPTY);
        lenient().when(packVersionService.findByItemId(any())).thenReturn(Optional.empty());
        lenient().when(qualityService.summariesByLegacyItemIds(any())).thenReturn(Map.of());
    }

    @Test
    void creatorRetrievesOwnDraftValidationPack() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        UUID questionId = UUID.randomUUID();
        UUID correctOptionId = UUID.randomUUID();
        UUID wrongOptionId = UUID.randomUUID();
        MarketplaceQuizPackSnapshot snapshot = snapshot(item, snapshotContent(questionId, correctOptionId, wrongOptionId));
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));

        CreatorValidationPackResponse response = service.getCreatorValidationPack("creator", itemId);

        assertThat(response.getItemId()).isEqualTo(itemId);
        assertThat(response.getSourceWorkspaceId()).isEqualTo(item.getSourceWorkspace().getWorkspaceId());
        assertThat(response.getTitle()).isEqualTo("Pack");
        assertThat(response.getChapterCount()).isEqualTo(1);
        assertThat(response.getQuizCount()).isEqualTo(1);
        assertThat(response.getQuestionCount()).isEqualTo(1);
        assertThat(response.getChapters()).hasSize(1);

        CreatorValidationPackResponse.ChapterResponse chapter = response.getChapters().get(0);
        assertThat(chapter.getSequenceNo()).isEqualTo(1);
        assertThat(chapter.getTitle()).isEqualTo("Chuong 1");
        assertThat(chapter.getSummary()).isEqualTo("Tom tat");
        assertThat(chapter.getQuizTitle()).isEqualTo("Quiz 1");
        assertThat(chapter.getQuestions()).hasSize(1);

        CreatorValidationPackResponse.QuestionResponse question = chapter.getQuestions().get(0);
        assertThat(question.getQuestionId()).isEqualTo(questionId);
        assertThat(question.getType()).isEqualTo("SINGLE_CHOICE");
        assertThat(question.getText()).isEqualTo("Hai cong hai bang may?");
        assertThat(question.getSequenceNo()).isEqualTo(1);
        assertThat(question.getOptions()).extracting(CreatorValidationPackResponse.OptionResponse::getOptionId)
                .containsExactly(correctOptionId, wrongOptionId);
    }

    @Test
    void validationPackDoesNotExposeCorrectAnswersForNonAdminDefaultPlan() throws Exception {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        UUID questionId = UUID.randomUUID();
        UUID correctOptionId = UUID.randomUUID();
        JsonNode content = snapshotContent(questionId, correctOptionId, UUID.randomUUID());
        MarketplaceQuizPackSnapshot snapshot = snapshot(item, content);
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));

        CreatorValidationPackResponse response = service.getCreatorValidationPack("creator", itemId);

        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("correct");
        assertThat(json).doesNotContain("explanation");
        // the correct option appears only as a normal option, never singled out by a field
        assertThat(response.getChapters().get(0).getQuestions().get(0).getOptions()).hasSize(2);
        // sanitizing must not mutate the persisted snapshot JSON
        assertThat(snapshot.getContent().at("/chapters/0/quiz/questions/0/options/0/correct").asBoolean()).isTrue();
    }

    @Test
    void adminDefaultValidationPackExposesCorrectAnswers() throws Exception {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        UUID questionId = UUID.randomUUID();
        UUID correctOptionId = UUID.randomUUID();
        UUID wrongOptionId = UUID.randomUUID();
        MarketplaceQuizPackSnapshot snapshot = snapshot(item, snapshotContent(questionId, correctOptionId, wrongOptionId));
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));
        when(subscriptionService.hasAdminDefaultPlan("creator")).thenReturn(true);

        CreatorValidationPackResponse response = service.getCreatorValidationPack("creator", itemId);

        List<CreatorValidationPackResponse.OptionResponse> options = response.getChapters().get(0)
                .getQuestions().get(0).getOptions();
        assertThat(options).extracting(CreatorValidationPackResponse.OptionResponse::getCorrect)
                .containsExactly(true, false);
        assertThat(new ObjectMapper().writeValueAsString(response)).contains("\"correct\":true", "\"correct\":false");
    }

    @Test
    void otherUserCannotRetrieveValidationPack() {
        UUID itemId = UUID.randomUUID();
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "intruder")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCreatorValidationPack("intruder", itemId))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND);
    }

    @Test
    void nonDraftItemCannotRetrieveOrRefreshValidationData() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.PENDING_REVIEW);
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.getCreatorValidationPack("creator", itemId))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        assertThatThrownBy(() -> service.refreshSnapshot("creator", itemId))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
    }

    @Test
    void refreshUpdatesSnapshotAndResetsValidationScore() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        item.setCreatorValidationScore(95);
        item.setReviewNote("Thieu noi dung chuong 2");
        MarketplaceQuizPackSnapshot snapshot = snapshot(item, objectMapper.createObjectNode());
        snapshot.setChapterCount(1);
        snapshot.setQuizCount(1);
        snapshot.setQuestionCount(1);
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));
        when(marketplaceItemRepository.save(any(MarketplaceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockWorkspaceGraph("creator", item.getSourceWorkspace());

        MarketplaceItemResponse response = service.refreshSnapshot("creator", itemId);

        assertThat(snapshot.getChapterCount()).isEqualTo(4);
        assertThat(snapshot.getQuizCount()).isEqualTo(4);
        assertThat(snapshot.getQuestionCount()).isEqualTo(20);
        assertThat(snapshot.getContent().path("chapters").size()).isEqualTo(4);
        assertThat(snapshot.getContent().at("/chapters/0/quiz/questions/0/evidence/explanation").asText())
                .isEqualTo("Giai thich 1");
        assertThat(item.getCreatorValidationScore()).isNull();
        assertThat(response.getCreatorValidationScore()).isNull();
        assertThat(response.getQuestionCount()).isEqualTo(20);
        // updated in place: the one existing snapshot record is saved, no new record created
        verify(snapshotRepository).save(snapshot);
    }

    @Test
    void refreshPreservesItemMetadataAndReviewNote() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        item.setReviewNote("Thieu noi dung chuong 2");
        MarketplaceQuizPackSnapshot snapshot = snapshot(item, objectMapper.createObjectNode());
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));
        when(marketplaceItemRepository.save(any(MarketplaceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockWorkspaceGraph("creator", item.getSourceWorkspace());

        MarketplaceItemResponse response = service.refreshSnapshot("creator", itemId);

        assertThat(response.getTitle()).isEqualTo("Pack");
        assertThat(response.getDescription()).isEqualTo("Mo ta");
        assertThat(response.getSubject()).isEqualTo("Toan");
        assertThat(response.getPriceCoins()).isEqualTo(100);
        assertThat(response.getStatus()).isEqualTo(MarketplaceItemStatus.DRAFT);
        assertThat(response.getSourceWorkspaceId()).isEqualTo(item.getSourceWorkspace().getWorkspaceId());
        assertThat(response.getReviewNote()).isEqualTo("Thieu noi dung chuong 2");
        assertThat(item.getCreator().getUserId()).isEqualTo("creator");
    }

    @Test
    void creatorCanSubmitAgainAfterRejectionReturnedItemToDraft() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        item.setCreatorValidationScore(95);
        item.setReviewNote("Da bi tu choi truoc do");
        MarketplaceQuizPackSnapshot snapshot = snapshot(item, objectMapper.createObjectNode());
        snapshot.setChapterCount(4);
        snapshot.setQuizCount(4);
        snapshot.setQuestionCount(20);
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator")).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));
        when(marketplaceItemRepository.save(any(MarketplaceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setVersionNo(1);
        version.setPack(pack);
        when(packVersionService.requireByItemId(itemId)).thenReturn(version);
        when(packVersionService.syncFromLegacyItem(item, snapshot)).thenReturn(Optional.of(version));
        when(qualityService.summary(version)).thenReturn(
                new MarketplaceQualityService.Summary(
                        com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus.PASSED,
                        100,
                        true
                )
        );

        MarketplaceItemResponse response = service.submitForReview("creator", itemId);

        assertThat(response.getStatus()).isEqualTo(MarketplaceItemStatus.PENDING_REVIEW);
        verify(qualityService).requireCurrentPass(version);
    }

    @Test
    void currentQualityPassIsRequiredBeforeSubmittingForReview() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = item(itemId, "creator", MarketplaceItemStatus.DRAFT);
        item.setCreatorValidationScore(95);
        MarketplacePackVersion version = new MarketplacePackVersion();
        when(marketplaceItemRepository.findByItemIdAndCreatorUserId(itemId, "creator"))
                .thenReturn(Optional.of(item));
        when(packVersionService.requireByItemId(itemId)).thenReturn(version);
        doThrow(new AppException(ErrorCode.MARKETPLACE_QUALITY_VALIDATION_REQUIRED))
                .when(qualityService).requireCurrentPass(version);

        assertThatThrownBy(() -> service.submitForReview("creator", itemId))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_QUALITY_VALIDATION_REQUIRED);

        assertThat(item.getStatus()).isEqualTo(MarketplaceItemStatus.DRAFT);
    }

    private MarketplaceItem item(UUID itemId, String userId, MarketplaceItemStatus status) {
        User creator = new User();
        creator.setUserId(userId);
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(creator);
        MarketplaceItem item = new MarketplaceItem();
        item.setItemId(itemId);
        item.setCreator(creator);
        item.setSourceWorkspace(workspace);
        item.setTitle("Pack");
        item.setDescription("Mo ta");
        item.setSubject("Toan");
        item.setPriceCoins(100);
        item.setStatus(status);
        return item;
    }

    private MarketplaceQuizPackSnapshot snapshot(MarketplaceItem item, JsonNode content) {
        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setItem(item);
        snapshot.setChapterCount(1);
        snapshot.setQuizCount(1);
        snapshot.setQuestionCount(1);
        snapshot.setContent(content);
        return snapshot;
    }

    private JsonNode snapshotContent(UUID questionId, UUID correctOptionId, UUID wrongOptionId) {
        ObjectNode content = new ObjectMapper().createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        ObjectNode chapter = chapters.addObject();
        chapter.put("sequenceNo", 1);
        chapter.put("title", "Chuong 1");
        chapter.put("summary", "Tom tat");
        ObjectNode quiz = chapter.putObject("quiz");
        quiz.put("title", "Quiz 1");
        ArrayNode questions = quiz.putArray("questions");
        ObjectNode question = questions.addObject();
        question.put("questionId", questionId.toString());
        question.put("type", "SINGLE_CHOICE");
        question.put("text", "Hai cong hai bang may?");
        question.put("explanation", "Boi vi 2+2=4");
        question.put("sequenceNo", 1);
        ArrayNode options = question.putArray("options");
        ObjectNode right = options.addObject();
        right.put("optionId", correctOptionId.toString());
        right.put("label", "A");
        right.put("text", "Bon");
        right.put("correct", true);
        right.put("sequenceNo", 1);
        ObjectNode wrong = options.addObject();
        wrong.put("optionId", wrongOptionId.toString());
        wrong.put("label", "B");
        wrong.put("text", "Nam");
        wrong.put("correct", false);
        wrong.put("sequenceNo", 2);
        return content;
    }

    private void mockWorkspaceGraph(String userId, StudyWorkspace workspace) {
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspace.getWorkspaceId(), userId, WorkspaceStatus.DELETED)).thenReturn(Optional.of(workspace));
        Roadmap roadmap = new Roadmap();
        roadmap.setRoadmapId(UUID.randomUUID());
        when(roadmapRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspace.getWorkspaceId()))
                .thenReturn(Optional.of(roadmap));

        List<RoadmapStep> steps = new ArrayList<>();
        for (int stepNo = 1; stepNo <= 4; stepNo++) {
            RoadmapStep step = new RoadmapStep();
            step.setStepId(UUID.randomUUID());
            step.setSequenceNo(stepNo);
            step.setTitle("Step " + stepNo);
            step.setSummary("Summary " + stepNo);
            steps.add(step);

            Quiz quiz = new Quiz();
            quiz.setQuizId(UUID.randomUUID());
            quiz.setTitle("Quiz " + stepNo);
            when(quizRepository.findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
                    step.getStepId(), userId, QuizStatus.ACTIVE)).thenReturn(Optional.of(quiz));

            List<QuizQuestion> questions = new ArrayList<>();
            List<QuizOption> options = new ArrayList<>();
            for (int questionNo = 1; questionNo <= 5; questionNo++) {
                QuizQuestion question = new QuizQuestion();
                question.setQuestionId(UUID.randomUUID());
                question.setType(QuizQuestionType.SINGLE_CHOICE);
                question.setQuestionText("Cau hoi " + questionNo);
                question.setExplanation("Giai thich " + questionNo);
                question.setSequenceNo(questionNo);
                questions.add(question);
                for (int optionNo = 1; optionNo <= 2; optionNo++) {
                    QuizOption option = new QuizOption();
                    option.setOptionId(UUID.randomUUID());
                    option.setQuestion(question);
                    option.setLabel(optionNo == 1 ? "A" : "B");
                    option.setOptionText("Dap an " + optionNo);
                    option.setCorrect(optionNo == 1);
                    option.setSequenceNo(optionNo);
                    options.add(option);
                }
            }
            when(quizQuestionRepository.findByQuizQuizIdOrderBySequenceNoAsc(quiz.getQuizId())).thenReturn(questions);
            when(quizOptionRepository.findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(quiz.getQuizId()))
                    .thenReturn(options);
        }
        when(roadmapStepRepository.findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId())).thenReturn(steps);
    }
}
