package com.skillsprint.service.learningstructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LearningStructureServiceTest {

    private static final int MAX_LENGTH = 90;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1. Tổng quan          | Tổng quan",
            "1) Tổng quan          | Tổng quan",
            "1.1. Backend          | Backend",
            "2 - Lỗi thường gặp    | Lỗi thường gặp",
            "3: Kết luận           | Kết luận",
            "4/ Tóm tắt            | Tóm tắt",
            "Bước 1: 1. Tổng quan  | Tổng quan",
            "Step 2 - 2. Backend   | Backend",
    })
    void cleanDisplayTitleStripsRawNumberingPrefixes(String input, String expected) {
        assertThat(LearningStructureService.cleanDisplayTitle(input, MAX_LENGTH))
                .isEqualTo(expected.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026 Roadmap",
            "404 Error Handling",
            "5 Whys",
            "Java 17 Basics",
            "10x Productivity",
    })
    void cleanDisplayTitleKeepsMeaningfulNumericTitles(String input) {
        assertThat(LearningStructureService.cleanDisplayTitle(input, MAX_LENGTH))
                .isEqualTo(input);
    }

    @Test
    void cleanDisplayTitleNormalizesWhitespace() {
        assertThat(LearningStructureService.cleanDisplayTitle("  Tổng    quan   ", MAX_LENGTH))
                .isEqualTo("Tổng quan");
    }

    @Test
    void cleanDisplayTitleFallsBackToDefaultWhenBlank() {
        assertThat(LearningStructureService.cleanDisplayTitle("   ", MAX_LENGTH))
                .isEqualTo("Nội dung học");
        assertThat(LearningStructureService.cleanDisplayTitle(null, MAX_LENGTH))
                .isEqualTo("Nội dung học");
    }

    @Test
    void cleanDisplayTitleKeepsBareNumberWithoutSeparator() {
        // No content after the separator -> nothing to strip, keep as-is.
        assertThat(LearningStructureService.cleanDisplayTitle("1.", MAX_LENGTH))
                .isEqualTo("1.");
    }

    @Test
    void cleanDisplayTitleTruncatesLongTitles() {
        String longTitle = "A".repeat(120);
        String result = LearningStructureService.cleanDisplayTitle(longTitle, MAX_LENGTH);
        assertThat(result).hasSize(MAX_LENGTH);
        assertThat(result).endsWith("...");
    }
}
