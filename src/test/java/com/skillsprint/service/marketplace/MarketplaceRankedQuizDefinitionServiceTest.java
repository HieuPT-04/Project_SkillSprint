package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRankedQuestionSelection;
import com.skillsprint.entity.MarketplaceRankedQuizDefinition;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceRankedQuestionSelectionRepository;
import com.skillsprint.repository.MarketplaceRankedQuizDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceRankedQuizDefinitionServiceTest {

    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplaceRankedQuizDefinitionRepository definitionRepository;
    @Mock MarketplaceRankedQuestionSelectionRepository selectionRepository;
    @InjectMocks MarketplaceRankedQuizDefinitionService service;

    @Test
    void createsFortyFiveSelectionsForNineChapters() {
        MarketplacePackVersion version = version(content(9, 5));
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(definitionRepository.findByPackVersionVersionId(version.getVersionId())).thenReturn(Optional.empty());
        when(definitionRepository.save(any(MarketplaceRankedQuizDefinition.class)))
                .thenAnswer(invocation -> {
                    MarketplaceRankedQuizDefinition definition = invocation.getArgument(0);
                    definition.setDefinitionId(UUID.randomUUID());
                    return definition;
                });
        when(selectionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MarketplaceRankedQuizDefinition definition = service.ensureDefinition(version.getVersionId());

        ArgumentCaptor<Iterable<MarketplaceRankedQuestionSelection>> selections = iterableCaptor();
        verify(selectionRepository).saveAll(selections.capture());
        List<MarketplaceRankedQuestionSelection> savedSelections = toList(selections.getValue());

        assertThat(definition.getQuestionsPerStep()).isEqualTo(5);
        assertThat(definition.getTotalQuestionCount()).isEqualTo(45);
        assertThat(definition.getDailyAttemptLimit()).isEqualTo(3);
        assertThat(savedSelections).hasSize(45);
        assertThat(savedSelections)
                .extracting(MarketplaceRankedQuestionSelection::getSourceStepKey)
                .containsExactlyInAnyOrder(
                        "chapter:1", "chapter:1", "chapter:1", "chapter:1", "chapter:1",
                        "chapter:2", "chapter:2", "chapter:2", "chapter:2", "chapter:2",
                        "chapter:3", "chapter:3", "chapter:3", "chapter:3", "chapter:3",
                        "chapter:4", "chapter:4", "chapter:4", "chapter:4", "chapter:4",
                        "chapter:5", "chapter:5", "chapter:5", "chapter:5", "chapter:5",
                        "chapter:6", "chapter:6", "chapter:6", "chapter:6", "chapter:6",
                        "chapter:7", "chapter:7", "chapter:7", "chapter:7", "chapter:7",
                        "chapter:8", "chapter:8", "chapter:8", "chapter:8", "chapter:8",
                        "chapter:9", "chapter:9", "chapter:9", "chapter:9", "chapter:9");
    }

    @Test
    void returnsExistingDefinitionWithoutWritingSelections() {
        MarketplacePackVersion version = version(content(1, 5));
        MarketplaceRankedQuizDefinition existing = new MarketplaceRankedQuizDefinition();
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(definitionRepository.findByPackVersionVersionId(version.getVersionId())).thenReturn(Optional.of(existing));

        MarketplaceRankedQuizDefinition result = service.ensureDefinition(version.getVersionId());

        assertThat(result).isSameAs(existing);
        verify(definitionRepository, never()).save(any());
        verify(selectionRepository, never()).saveAll(any());
    }

    @Test
    void rejectsChapterWithFewerThanFiveQuestionsBeforeWriting() {
        MarketplacePackVersion version = version(content(2, 5));
        version.setContent(contentWithQuestionCount(2, 4, 5));
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(definitionRepository.findByPackVersionVersionId(version.getVersionId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureDefinition(version.getVersionId()))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE));

        verify(definitionRepository, never()).save(any());
        verify(selectionRepository, never()).saveAll(any());
    }

    @Test
    void rejectsQuestionWithMultipleCorrectOptionsBeforeWriting() {
        MarketplacePackVersion version = version(content(1, 5));
        ObjectNode firstOption = (ObjectNode) version.getContent()
                .path("chapters").get(0).path("quiz").path("questions").get(0).path("options").get(1);
        firstOption.put("correct", true);
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(definitionRepository.findByPackVersionVersionId(version.getVersionId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ensureDefinition(version.getVersionId()))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_RANKED_DEFINITION_UNAVAILABLE));

        verify(definitionRepository, never()).save(any());
        verify(selectionRepository, never()).saveAll(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Iterable<MarketplaceRankedQuestionSelection>> iterableCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
    }

    private List<MarketplaceRankedQuestionSelection> toList(
            Iterable<MarketplaceRankedQuestionSelection> selections
    ) {
        List<MarketplaceRankedQuestionSelection> values = new ArrayList<>();
        selections.forEach(values::add);
        return values;
    }

    private MarketplacePackVersion version(JsonNode content) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setContent(content);
        return version;
    }

    private JsonNode content(int chapterCount, int questionsPerChapter) {
        return contentWithQuestionCount(chapterCount, questionsPerChapter, questionsPerChapter);
    }

    private JsonNode contentWithQuestionCount(int chapterCount, int firstChapterQuestionCount, int otherQuestionCount) {
        ObjectNode content = new ObjectMapper().createObjectNode();
        ArrayNode chapters = content.putArray("chapters");
        for (int chapterNumber = 1; chapterNumber <= chapterCount; chapterNumber++) {
            ObjectNode chapter = chapters.addObject();
            ObjectNode quiz = chapter.putObject("quiz");
            ArrayNode questions = quiz.putArray("questions");
            int questionCount = chapterNumber == 1 ? firstChapterQuestionCount : otherQuestionCount;
            for (int questionNumber = 1; questionNumber <= questionCount; questionNumber++) {
                ObjectNode question = questions.addObject();
                question.put("questionId", UUID.randomUUID().toString());
                ArrayNode options = question.putArray("options");
                options.addObject().put("optionId", UUID.randomUUID().toString()).put("correct", true);
                options.addObject().put("optionId", UUID.randomUUID().toString()).put("correct", false);
            }
        }
        return content;
    }
}
