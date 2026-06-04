package com.skillsprint.dto.response.quiz;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizOptionResponse {

    UUID optionId;
    String label;
    String text;
}
