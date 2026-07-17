package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.entity.MarketplaceRankedQuestionSelection;
import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import com.skillsprint.repository.MarketplaceRankedQuestionSelectionRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class MarketplaceRankedAttemptServiceTest {

    @Mock MarketplaceRankedQuizAccessService accessService;
    @Mock MarketplaceRankedQuizDefinitionService definitionService;
    @Mock MarketplaceRankedQuestionSelectionRepository selectionRepository;
    @Mock MarketplaceRankedAttemptRepository attemptRepository;
    @Mock UserRepository userRepository;

    ObjectMapper objectMapper = new ObjectMapper();
    MarketplaceRankedAttemptService service;
    MarketplacePackVersion version;
    MarketplaceRankedQuizDefinition definition;
    User buyer;

    @BeforeEach
    void setUp() {
        service = new MarketplaceRankedAttemptService(
                accessService,
                definitionService,
                selectionRepository,
                attemptRepository,
                userRepository,
                objectMapper
        );
        version = version(content(9, 5));
        definition = definition(version, 45, 3);
        buyer = new User();
        buyer.setUserId("buyer");
    }

    @Test
    void startsFortyFiveQuestionAttemptWithoutLeakingAnswers() throws Exception {
        allowStart();
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                "buyer", version.getVersionId(), MarketplaceRankedAttemptStatus.IN_PROGRESS)).thenReturn(List.of());
        when(attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                eq("buyer"), eq(version.getVersionId()), any(LocalDate.class))).thenReturn(0L, 1L);
        when(selectionRepository.findByDefinitionDefinitionIdOrderByStepOrderAscSelectionOrderAsc(
                definition.getDefinitionId())).thenReturn(selections(version.getContent()));
        when(userRepository.findById("buyer")).thenReturn(Optional.of(buyer));
        when(attemptRepository.save(any(MarketplaceRankedAttempt.class))).thenAnswer(invocation -> {
            MarketplaceRankedAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(UUID.randomUUID());
            return attempt;
        });

        MarketplaceRankedAttemptResponse response = service.startOrResume("buyer", version.getVersionId());

        assertThat(response.getQuestions()).hasSize(45);
        assertThat(response.getAttemptsRemaining()).isEqualTo(2);
        assertThat(objectMapper.writeValueAsString(response.getQuestions())).doesNotContain("correct", "explanation");
        MarketplaceRankedAttempt persisted = captureSavedAttempt();
        assertThat(persisted.getAnswerSnapshot().at("/answers/0/correctOptionId").asText()).isNotBlank();
        assertThat(persisted.getQuestionSnapshot().toString()).doesNotContain("correct", "explanation");
    }

    @Test
    void resumesExistingNonExpiredAttemptWithoutCreatingAnother() {
        allowStart();
        MarketplaceRankedAttempt existing = inProgressAttempt();
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                "buyer", version.getVersionId(), MarketplaceRankedAttemptStatus.IN_PROGRESS)).thenReturn(List.of(existing));
        when(attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                eq("buyer"), eq(version.getVersionId()), any(LocalDate.class))).thenReturn(1L);

        MarketplaceRankedAttemptResponse response = service.startOrResume("buyer", version.getVersionId());

        assertThat(response.getAttemptId()).isEqualTo(existing.getAttemptId());
        assertThat(response.getQuestions()).hasSize(1);
        verify(attemptRepository, never()).save(any(MarketplaceRankedAttempt.class));
        verify(selectionRepository, never()).findByDefinitionDefinitionIdOrderByStepOrderAscSelectionOrderAsc(any());
    }

    @Test
    void rejectsNewAttemptWhenVietnamDailyLimitIsReached() {
        allowStart();
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                "buyer", version.getVersionId(), MarketplaceRankedAttemptStatus.IN_PROGRESS)).thenReturn(List.of());
        when(attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                eq("buyer"), eq(version.getVersionId()), any(LocalDate.class))).thenReturn(3L);

        assertThatThrownBy(() -> service.startOrResume("buyer", version.getVersionId()))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_RANKED_ATTEMPT_LIMIT_REACHED));

        verify(attemptRepository, never()).save(any(MarketplaceRankedAttempt.class));
        verify(selectionRepository, never()).findByDefinitionDefinitionIdOrderByStepOrderAscSelectionOrderAsc(any());
    }

    @Test
    void expiresStaleAttemptBeforeStartingTheNextOne() {
        allowStart();
        MarketplaceRankedAttempt expired = inProgressAttempt();
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                "buyer", version.getVersionId(), MarketplaceRankedAttemptStatus.IN_PROGRESS)).thenReturn(List.of(expired));
        when(attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                eq("buyer"), eq(version.getVersionId()), any(LocalDate.class))).thenReturn(1L, 2L);
        when(selectionRepository.findByDefinitionDefinitionIdOrderByStepOrderAscSelectionOrderAsc(
                definition.getDefinitionId())).thenReturn(selections(version.getContent()));
        when(userRepository.findById("buyer")).thenReturn(Optional.of(buyer));
        when(attemptRepository.save(any(MarketplaceRankedAttempt.class))).thenAnswer(invocation -> {
            MarketplaceRankedAttempt attempt = invocation.getArgument(0);
            attempt.setAttemptId(UUID.randomUUID());
            return attempt;
        });

        MarketplaceRankedAttemptResponse response = service.startOrResume("buyer", version.getVersionId());

        assertThat(expired.getStatus()).isEqualTo(MarketplaceRankedAttemptStatus.EXPIRED);
        assertThat(response.getAttemptNumber()).isEqualTo(2);
        verify(attemptRepository).saveAll(List.of(expired));
    }

    @Test
    void returnsCurrentAttemptWithItsPersistedQuestionOrder() {
        MarketplaceRankedAttempt existing = inProgressAttempt();
        when(accessService.requireRankedAccess("buyer", version.getVersionId())).thenReturn(version);
        when(attemptRepository.findByBuyerUserIdAndPackVersionVersionIdAndStatusOrderByStartedAtDesc(
                "buyer", version.getVersionId(), MarketplaceRankedAttemptStatus.IN_PROGRESS)).thenReturn(List.of(existing));
        when(attemptRepository.countByBuyerUserIdAndPackVersionVersionIdAndAttemptDate(
                eq("buyer"), eq(version.getVersionId()), any(LocalDate.class))).thenReturn(1L);

        MarketplaceRankedAttemptResponse response = service.getInProgress("buyer", version.getVersionId());

        assertThat(response.getAttemptId()).isEqualTo(existing.getAttemptId());
        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getQuestions().get(0).getQuestionId())
                .isEqualTo(UUID.fromString(existing.getQuestionSnapshot().at("/questions/0/questionId").asText()));
    }

    private MarketplaceRankedAttempt captureSavedAttempt() {
        ArgumentCaptor<MarketplaceRankedAttempt> attempts = ArgumentCaptor.forClass(MarketplaceRankedAttempt.class);
        verify(attemptRepository).save(attempts.capture());
        return attempts.getValue();
    }

    private void allowStart() {
        when(accessService.requireAndLockRankedAccess("buyer", version.getVersionId())).thenReturn(version);
        when(definitionService.ensureDefinition(version.getVersionId())).thenReturn(definition);
    }

    private MarketplaceRankedAttempt inProgressAttempt() {
        MarketplaceRankedAttempt attempt = new MarketplaceRankedAttempt();
        attempt.setAttemptId(UUID.randomUUID());
        attempt.setPackVersion(version);
        attempt.setDefinition(definition);
        attempt.setAttemptDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        attempt.setAttemptNumber(1);
        attempt.setStatus(MarketplaceRankedAttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(Instant.now().minusSeconds(60));
        attempt.setExpiresAt(Instant.now().plusSeconds(600));
        ObjectNode snapshot = objectMapper.createObjectNode();
        ArrayNode questions = snapshot.putArray("questions");
        ObjectNode question = questions.addObject();
        question.put("questionId", UUID.randomUUID().toString());
        question.put("type", "SINGLE_CHOICE");
        question.put("text", "Question");
        question.putArray("options").addObject()
                .put("optionId", UUID.randomUUID().toString())
                .put("label", "A")
                .put("text", "Answer");
        attempt.setQuestionSnapshot(snapshot);
        return attempt;
    }

    private MarketplacePackVersion version(JsonNode content) {
        MarketplacePackVersion value = new MarketplacePackVersion();
        value.setVersionId(UUID.randomUUID());
        value.setVersionNo(1);
        value.setContent(content);
        return value;
    }

    private MarketplaceRankedQuizDefinition definition(
            MarketplacePackVersion packVersion,
            int totalQuestionCount,
            int dailyAttemptLimit
    ) {
        MarketplaceRankedQuizDefinition value = new MarketplaceRankedQuizDefinition();
        value.setDefinitionId(UUID.randomUUID());
        value.setPackVersion(packVersion);
        value.setTotalQuestionCount(totalQuestionCount);
        value.setDailyAttemptLimit(dailyAttemptLimit);
        return value;
    }

    private List<MarketplaceRankedQuestionSelection> selections(JsonNode content) {
        List<MarketplaceRankedQuestionSelection> values = new ArrayList<>();
        int stepOrder = 0;
        for (JsonNode chapter : content.path("chapters")) {
            stepOrder++;
            int selectionOrder = 0;
            for (JsonNode question : chapter.path("quiz").path("questions")) {
                selectionOrder++;
                MarketplaceRankedQuestionSelection selection = new MarketplaceRankedQuestionSelection();
                selection.setQuestionId(UUID.fromString(question.path("questionId").asText()));
                selection.setStepOrder(stepOrder);
                selection.setSelectionOrder(selectionOrder);
                values.add(selection);
            }
        }
        return values;
    }

    private JsonNode content(int chapterCount, int questionsPerChapter) {
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        for (int chapterNumber = 1; chapterNumber <= chapterCount; chapterNumber++) {
            ArrayNode questions = chapters.addObject().putObject("quiz").putArray("questions");
            for (int questionNumber = 1; questionNumber <= questionsPerChapter; questionNumber++) {
                ObjectNode question = questions.addObject();
                question.put("questionId", UUID.randomUUID().toString());
                question.put("type", "SINGLE_CHOICE");
                question.put("text", "Question " + chapterNumber + '-' + questionNumber);
                question.put("explanation", "Hidden explanation");
                ArrayNode options = question.putArray("options");
                options.addObject().put("optionId", UUID.randomUUID().toString())
                        .put("label", "A").put("text", "Correct").put("correct", true);
                options.addObject().put("optionId", UUID.randomUUID().toString())
                        .put("label", "B").put("text", "Wrong").put("correct", false);
            }
        }
        return content;
    }
}
