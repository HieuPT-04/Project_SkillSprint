package com.skillsprint.service.learningstructure;

import com.skillsprint.entity.MaterialChunk;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class LearningDocumentAnalyzer {

    private static final Pattern SLOT_PATTERN = Pattern.compile("\\d{1,2}");
    private static final List<String> SYLLABUS_KEYWORDS = List.of(
            "syllabus name",
            "syllabus code",
            "course description",
            "course objectives",
            "learning materials",
            "session schedule",
            "assessment scheme",
            "chu de bai hoc",
            "muc tieu hoc phan",
            "ke hoach hoc tap",
            "tieu chi danh gia"
    );

    private LearningDocumentAnalyzer() {
    }

    public static DocumentAnalysis analyze(List<MaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new DocumentAnalysis(DocumentKind.GENERAL, List.of());
        }

        List<SyllabusSlot> syllabusSlots = extractSyllabusSlots(chunks);
        DocumentKind kind = isSyllabus(chunks, syllabusSlots)
                ? DocumentKind.SYLLABUS
                : DocumentKind.GENERAL;

        return new DocumentAnalysis(kind, syllabusSlots);
    }

    private static boolean isSyllabus(List<MaterialChunk> chunks, List<SyllabusSlot> syllabusSlots) {
        if (syllabusSlots.size() >= 3) {
            return true;
        }

        String text = normalize(chunks.stream()
                .map(MaterialChunk::getContent)
                .filter(Objects::nonNull)
                .reduce("", (left, right) -> left + System.lineSeparator() + right));

        long matchedKeywords = SYLLABUS_KEYWORDS.stream()
                .filter(text::contains)
                .count();

        return matchedKeywords >= 3;
    }

    private static List<SyllabusSlot> extractSyllabusSlots(List<MaterialChunk> chunks) {
        Map<String, SyllabusSlot> slots = new LinkedHashMap<>();

        for (MaterialChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            String sourceChunkId = toChunkId(chunk.getChunkId());
            for (String line : chunk.getContent().lines().toList()) {
                List<String> parts = splitTableLine(line);
                if (parts.size() < 2) {
                    continue;
                }

                for (int i = 0; i < parts.size() - 1; i++) {
                    String slotValue = parts.get(i);
                    String topic = cleanTitle(parts.get(i + 1));
                    if (!SLOT_PATTERN.matcher(slotValue).matches() || !isValidTopic(topic)) {
                        continue;
                    }

                    int slotNo = Integer.parseInt(slotValue);
                    List<String> details = collectDetails(parts, i + 2);
                    String key = slotNo + ":" + normalize(topic);
                    slots.putIfAbsent(key, new SyllabusSlot(slotNo, topic, details, safeSourceIds(sourceChunkId)));
                }
            }
        }

        return new ArrayList<>(slots.values());
    }

    private static List<String> splitTableLine(String line) {
        if (line == null || !line.contains("|")) {
            return List.of();
        }
        return List.of(line.split("\\|")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static List<String> collectDetails(List<String> parts, int startIndex) {
        List<String> details = new ArrayList<>();
        for (int i = startIndex; i < Math.min(parts.size(), startIndex + 4); i++) {
            String value = cleanTitle(parts.get(i));
            if (isUsefulDetail(value)) {
                details.add(value);
            }
        }
        return details.stream().distinct().limit(4).toList();
    }

    private static boolean isUsefulDetail(String value) {
        String normalized = normalize(value);
        return !normalized.isBlank()
                && !normalized.equals("lop hoc")
                && !normalized.equals("class")
                && !normalized.equals("hinh thuc")
                && !normalized.equals("slot");
    }

    private static boolean isValidTopic(String topic) {
        String normalized = normalize(topic);
        return topic.length() >= 4
                && topic.length() <= 140
                && !normalized.contains("chu de bai hoc")
                && !normalized.contains("topic")
                && !normalized.contains("hinh thuc")
                && !normalized.contains("assessment component")
                && !normalized.contains("weight")
                && !normalized.equals("lop hoc")
                && !normalized.equals("class");
    }

    private static String cleanTitle(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static List<String> safeSourceIds(String sourceChunkId) {
        return sourceChunkId == null ? List.of() : List.of(sourceChunkId);
    }

    private static String toChunkId(UUID chunkId) {
        return chunkId == null ? null : chunkId.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase().trim();
    }

    public enum DocumentKind {
        GENERAL,
        SYLLABUS
    }

    public record DocumentAnalysis(
            DocumentKind kind,
            List<SyllabusSlot> syllabusSlots
    ) {
        public boolean isSyllabus() {
            return kind == DocumentKind.SYLLABUS;
        }
    }

    public record SyllabusSlot(
            int slot,
            String topic,
            List<String> details,
            List<String> sourceChunkIds
    ) {
    }
}
