package com.skillsprint.dto.response.learningstructure;

import com.skillsprint.enums.learningstructure.DifficultyLevel;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChapterResponse {

    UUID chapterId;
    String title;
    String summary;
    List<String> whatToLearn;
    List<String> keyConcepts;
    List<String> learningOutcomes;
    List<String> recommendedFocus;
    DifficultyLevel difficulty;
    Integer estimatedMinutes;
    Integer sequenceNo;
    List<String> sourceChunkIds;
    List<TopicResponse> topics;
}
