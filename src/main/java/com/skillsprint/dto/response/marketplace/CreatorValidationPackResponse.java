package com.skillsprint.dto.response.marketplace;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreatorValidationPackResponse {

    UUID itemId;
    UUID packId;
    UUID versionId;
    Integer versionNo;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OptionResponse {
        UUID optionId;
        String label;
        String text;
        Integer sequenceNo;

        // The snapshot answer key is exposed only to the ADMIN_DEFAULT test plan.
        // Null is omitted from JSON for every other user.
        Boolean correct;
    }
}
