package com.skillsprint.mapper;

import com.skillsprint.dto.response.learningstructure.ChapterResponse;
import com.skillsprint.dto.response.learningstructure.LearningStructureResponse;
import com.skillsprint.dto.response.learningstructure.TopicResponse;
import com.skillsprint.entity.Chapter;
import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.entity.Topic;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LearningStructureMapper {

    public LearningStructureResponse toResponse(
            LearningStructureVersion structureVersion,
            List<Chapter> chapters,
            List<Topic> topics
    ) {
        Map<UUID, List<Topic>> topicsByChapterId = topics.stream()
                .collect(Collectors.groupingBy(topic -> topic.getChapter().getChapterId()));

        return LearningStructureResponse.builder()
                .structureVersionId(structureVersion.getStructureVersionId())
                .workspaceId(structureVersion.getWorkspace().getWorkspaceId())
                .versionNo(structureVersion.getVersionNo())
                .status(structureVersion.getStatus())
                .generatedBy(structureVersion.getGeneratedBy())
                .aiModel(structureVersion.getAiModel())
                .confidenceScore(structureVersion.getConfidenceScore())
                .inputSummary(structureVersion.getInputSummary())
                .warnings(structureVersion.getWarnings())
                .createdAt(structureVersion.getCreatedAt())
                .confirmedAt(structureVersion.getConfirmedAt())
                .chapters(chapters.stream()
                        .map(chapter -> toChapterResponse(
                                chapter,
                                topicsByChapterId.getOrDefault(chapter.getChapterId(), List.of())
                        ))
                        .toList())
                .build();
    }

    private ChapterResponse toChapterResponse(Chapter chapter, List<Topic> topics) {
        return ChapterResponse.builder()
                .chapterId(chapter.getChapterId())
                .title(chapter.getTitle())
                .summary(chapter.getSummary())
                .whatToLearn(chapter.getWhatToLearn())
                .keyConcepts(chapter.getKeyConcepts())
                .learningOutcomes(chapter.getLearningOutcomes())
                .recommendedFocus(chapter.getRecommendedFocus())
                .difficulty(chapter.getDifficulty())
                .estimatedMinutes(chapter.getEstimatedMinutes())
                .sequenceNo(chapter.getSequenceNo())
                .sourceChunkIds(chapter.getSourceChunkIds())
                .topics(topics.stream().map(this::toTopicResponse).toList())
                .build();
    }

    private TopicResponse toTopicResponse(Topic topic) {
        return TopicResponse.builder()
                .topicId(topic.getTopicId())
                .title(topic.getTitle())
                .summaryContent(topic.getSummaryContent())
                .whatToLearn(topic.getWhatToLearn())
                .keyConcepts(topic.getKeyConcepts())
                .learningOutcomes(topic.getLearningOutcomes())
                .recommendedFocus(topic.getRecommendedFocus())
                .difficulty(topic.getDifficulty())
                .estimatedMinutes(topic.getEstimatedMinutes())
                .sequenceNo(topic.getSequenceNo())
                .sourceChunkIds(topic.getSourceChunkIds())
                .build();
    }
}
