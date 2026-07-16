package com.skillsprint.dto.response.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PurchasedQuizPackResponse {
    UUID itemId; UUID packId; UUID versionId; Integer versionNo;
    String title; String subject; Integer questionCount; JsonNode content;
}
