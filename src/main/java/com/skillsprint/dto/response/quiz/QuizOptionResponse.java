package com.skillsprint.dto.response.quiz;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizOptionResponse {

    UUID optionId;
    String label;
    String text;

    // Anti-scraping: left null (and omitted from the JSON entirely via NON_NULL) for
    // regular users so the correct answer is only revealed at submission. Populated
    // ONLY for ADMIN_DEFAULT test flows so the FE auto-fill tool can pre-select.
    Boolean correct;
}
