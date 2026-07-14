package com.skillsprint.dto.response.marketplace;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreatorValidationPackResponse {

    UUID itemId;
    UUID sourceWorkspaceId;
    String title;
    Integer chapterCount;
    Integer quizCount;
    Integer questionCount;
    List<ChapterResponse> chapters;

    @Getter
    @Builder
    public static class ChapterResponse {
        Integer sequenceNo;
        String title;
        String summary;
        String quizTitle;
        List<QuestionResponse> questions;
    }

    @Getter
    @Builder
    public static class QuestionResponse {
        UUID questionId;
        String type;
        String text;
        Integer sequenceNo;
        List<OptionResponse> options;
    }

    @Getter
    @Builder
    public static class OptionResponse {
        UUID optionId;
        String label;
        String text;
        Integer sequenceNo;
    }
}
