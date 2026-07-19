package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.entity.MarketplacePackVersion;
import org.junit.jupiter.api.Test;

class MarketplaceQualityFingerprintTest {

    ObjectMapper objectMapper = new ObjectMapper();
    MarketplaceQualityFingerprint fingerprint = new MarketplaceQualityFingerprint(objectMapper);

    @Test
    void fingerprintIsStableForUnchangedSnapshotAndChangesWithContent() {
        MarketplacePackVersion version = version("Câu hỏi ban đầu");

        String first = fingerprint.of(version);
        String second = fingerprint.of(version);

        ((ObjectNode) version.getContent().path("chapters").get(0)
                .path("quiz").path("questions").get(0)).put("text", "Câu hỏi đã đổi");

        assertThat(first).hasSize(64).isEqualTo(second);
        assertThat(fingerprint.of(version)).isNotEqualTo(first);
    }

    private MarketplacePackVersion version(String questionText) {
        ObjectNode content = objectMapper.createObjectNode();
        ObjectNode question = content.putArray("chapters").addObject()
                .putObject("quiz").putArray("questions").addObject();
        question.put("text", questionText);

        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setChapterCount(1);
        version.setQuizCount(1);
        version.setQuestionCount(1);
        version.setContent(content);
        return version;
    }
}
