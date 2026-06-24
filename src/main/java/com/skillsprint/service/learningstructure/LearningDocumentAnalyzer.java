package com.skillsprint.service.learningstructure;

import com.skillsprint.entity.MaterialChunk;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LearningDocumentAnalyzer {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,5})\\s+(.{3,160})$");
    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+){0,4})[.)]?\\s+(.{3,160})$");
    private static final Pattern SLOT_PATTERN = Pattern.compile("\\d{1,2}");
    private static final Pattern SLIDE_PATTERN = Pattern.compile("^(slide|page|trang)\\s*\\d{1,3}[:.)-]?.*", Pattern.CASE_INSENSITIVE);
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
    private static final List<String> ASSIGNMENT_KEYWORDS = List.of(
            "assignment",
            "bai tap",
            "yeu cau",
            "requirements",
            "submission",
            "deadline",
            "rubric",
            "deliverables",
            "nop bai",
            "tieu chi cham"
    );
    private static final List<String> REPORT_HEADING_KEYWORDS = List.of(
            "tong quan",
            "loi ",
            "van de",
            "cach sua",
            "file lien quan",
            "thay doi chinh",
            "fallback",
            "prompt gemini",
            "gemini request",
            "tests da them",
            "build",
            "test result",
            "files changed",
            "ket luan",
            "root cause",
            "fix implemented",
            "verification",
            "final result"
    );

    private LearningDocumentAnalyzer() {
    }

    public static DocumentAnalysis analyze(List<MaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new DocumentAnalysis(DocumentKind.GENERAL, List.of(), List.of(), List.of());
        }

        List<SyllabusSlot> syllabusSlots = extractSyllabusSlots(chunks);
        List<DocumentSection> sections = extractSections(chunks);
        String normalizedText = normalizeForMatching(joinChunkText(chunks));
        List<String> signals = collectSignals(normalizedText, chunks, syllabusSlots, sections);
        DocumentKind kind = detectKind(normalizedText, chunks, syllabusSlots, sections);

        return new DocumentAnalysis(kind, syllabusSlots, sections, signals);
    }

    private static DocumentKind detectKind(
            String normalizedText,
            List<MaterialChunk> chunks,
            List<SyllabusSlot> syllabusSlots,
            List<DocumentSection> sections
    ) {
        if (syllabusSlots.size() >= 3 || countMatches(normalizedText, SYLLABUS_KEYWORDS) >= 3) {
            return DocumentKind.SYLLABUS;
        }
        if (isLikelyAssignment(normalizedText)) {
            return DocumentKind.ASSIGNMENT;
        }
        if (isLikelyReportSections(sections)) {
            return DocumentKind.LECTURE_NOTE;
        }
        if (isLikelySlideDeck(chunks, sections)) {
            return DocumentKind.SLIDE_DECK;
        }
        if (sections.size() >= 2) {
            return DocumentKind.LECTURE_NOTE;
        }
        return DocumentKind.GENERAL;
    }

    private static boolean isLikelyAssignment(String normalizedText) {
        return countMatches(normalizedText, ASSIGNMENT_KEYWORDS) >= 3;
    }

    private static boolean isLikelySlideDeck(List<MaterialChunk> chunks, List<DocumentSection> sections) {
        long slideLines = chunks.stream()
                .map(MaterialChunk::getContent)
                .filter(Objects::nonNull)
                .flatMap(content -> content.lines())
                .map(String::trim)
                .filter(line -> SLIDE_PATTERN.matcher(line).matches())
                .count();

        long shortSections = sections.stream()
                .filter(section -> section.text().length() <= 700)
                .count();

        return slideLines >= 3 || sections.size() >= 5 && shortSections >= sections.size() * 0.8;
    }

    private static List<String> collectSignals(
            String normalizedText,
            List<MaterialChunk> chunks,
            List<SyllabusSlot> syllabusSlots,
            List<DocumentSection> sections
    ) {
        List<String> signals = new ArrayList<>();
        if (!syllabusSlots.isEmpty()) {
            signals.add("syllabusSlots=" + syllabusSlots.size());
        }
        if (!sections.isEmpty()) {
            signals.add("sections=" + sections.size());
        }
        if (countMatches(normalizedText, SYLLABUS_KEYWORDS) >= 3) {
            signals.add("syllabusKeywords");
        }
        if (isLikelyAssignment(normalizedText)) {
            signals.add("assignmentKeywords");
        }
        if (isLikelySlideDeck(chunks, sections)) {
            signals.add("slideSignals");
        }
        if (isLikelyReportSections(sections)) {
            signals.add("technicalReportHeadings");
        }
        return signals;
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
                    String topic = cleanText(parts.get(i + 1));
                    if (!SLOT_PATTERN.matcher(slotValue).matches() || !isValidSyllabusTopic(topic)) {
                        continue;
                    }

                    int slotNo = Integer.parseInt(slotValue);
                    List<String> details = collectSlotDetails(parts, i + 2);
                    String key = slotNo + ":" + normalizeForMatching(topic);
                    slots.putIfAbsent(key, new SyllabusSlot(slotNo, topic, details, safeSourceIds(sourceChunkId)));
                }
            }
        }

        return new ArrayList<>(slots.values());
    }

    private static List<DocumentSection> extractSections(List<MaterialChunk> chunks) {
        List<DocumentSection> sections = new ArrayList<>();
        MutableSection currentSection = null;

        for (MaterialChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }

            for (String line : chunk.getContent().lines().toList()) {
                String trimmed = cleanText(line);
                if (trimmed.isBlank()) {
                    continue;
                }

                Heading heading = parseHeading(trimmed);
                if (heading != null) {
                    if (currentSection != null) {
                        sections.add(currentSection.toSection());
                    }
                    currentSection = new MutableSection(heading.level(), heading.title());
                    currentSection.addSourceChunkId(toChunkId(chunk.getChunkId()));
                    continue;
                }

                if (currentSection != null) {
                    currentSection.append(trimmed);
                    currentSection.addSourceChunkId(toChunkId(chunk.getChunkId()));
                }
            }
        }

        if (currentSection != null) {
            sections.add(currentSection.toSection());
        }

        return sections;
    }

    private static Heading parseHeading(String line) {
        if (line.contains("|") || line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
            return null;
        }

        Matcher markdownMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
        if (markdownMatcher.matches()) {
            return new Heading(markdownMatcher.group(1).length(), cleanText(markdownMatcher.group(2)));
        }

        Matcher numberedMatcher = NUMBERED_HEADING_PATTERN.matcher(line);
        if (numberedMatcher.matches()) {
            String outlineNumber = numberedMatcher.group(1);
            String title = cleanText(numberedMatcher.group(2));
            if (!isLikelyHeading(outlineNumber, title)) {
                return null;
            }
            return new Heading(outlineNumber.split("\\.").length, title);
        }

        if (isLikelyReportHeading(line)) {
            return new Heading(2, line);
        }

        if (SLIDE_PATTERN.matcher(line).matches()) {
            return new Heading(1, line);
        }

        return null;
    }

    private static boolean isLikelyHeading(String outlineNumber, String title) {
        int level = outlineNumber.split("\\.").length;
        if (level > 1) {
            return true;
        }

        int wordCount = title.split("\\s+").length;
        return title.length() <= 90
                && wordCount <= 12
                && !title.endsWith(".")
                && !title.endsWith(",")
                && !title.endsWith(";")
                && !title.endsWith(":");
    }

    private static boolean isLikelyReportHeading(String line) {
        String normalized = normalizeForMatching(line);
        if (countMatches(normalized, REPORT_HEADING_KEYWORDS) == 0) {
            return false;
        }
        int wordCount = line.split("\\s+").length;
        return line.length() <= 110
                && wordCount <= 14
                && !line.endsWith(".")
                && !line.endsWith(",")
                && !line.endsWith(";")
                && !line.contains("|");
    }

    private static boolean isLikelyReportSections(List<DocumentSection> sections) {
        if (sections.size() < 4) {
            return false;
        }
        long reportHeadings = sections.stream()
                .map(DocumentSection::title)
                .map(LearningDocumentAnalyzer::normalizeForMatching)
                .filter(title -> countMatches(title, REPORT_HEADING_KEYWORDS) > 0)
                .count();
        return reportHeadings >= 4 && reportHeadings * 2 >= sections.size();
    }

    private static List<String> splitTableLine(String line) {
        if (line == null || !line.contains("|")) {
            return List.of();
        }
        return Arrays.stream(line.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static List<String> collectSlotDetails(List<String> parts, int startIndex) {
        List<String> details = new ArrayList<>();
        for (int i = startIndex; i < Math.min(parts.size(), startIndex + 4); i++) {
            String value = cleanText(parts.get(i));
            if (isUsefulDetail(value)) {
                details.add(value);
            }
        }
        return details.stream().distinct().limit(4).toList();
    }

    private static boolean isUsefulDetail(String value) {
        String normalized = normalizeForMatching(value);
        return !normalized.isBlank()
                && !normalized.equals("lop hoc")
                && !normalized.equals("class")
                && !normalized.equals("hinh thuc")
                && !normalized.equals("slot");
    }

    private static boolean isValidSyllabusTopic(String topic) {
        String normalized = normalizeForMatching(topic);
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

    private static long countMatches(String normalizedText, List<String> keywords) {
        return keywords.stream()
                .filter(normalizedText::contains)
                .count();
    }

    private static String joinChunkText(List<MaterialChunk> chunks) {
        return chunks.stream()
                .map(MaterialChunk::getContent)
                .filter(Objects::nonNull)
                .reduce("", (left, right) -> left + System.lineSeparator() + right);
    }

    private static String cleanText(String value) {
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

    static String normalizeForMatching(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public enum DocumentKind {
        SYLLABUS,
        LECTURE_NOTE,
        SLIDE_DECK,
        ASSIGNMENT,
        GENERAL
    }

    public record DocumentAnalysis(
            DocumentKind kind,
            List<SyllabusSlot> syllabusSlots,
            List<DocumentSection> sections,
            List<String> signals
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

    public record DocumentSection(
            int level,
            String title,
            String text,
            List<String> sourceChunkIds
    ) {
    }

    private record Heading(int level, String title) {
    }

    private static class MutableSection {

        private final int level;
        private final String title;
        private final StringBuilder text = new StringBuilder();
        private final List<String> sourceChunkIds = new ArrayList<>();

        MutableSection(int level, String title) {
            this.level = level;
            this.title = title;
        }

        void append(String line) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append(line);
        }

        void addSourceChunkId(String sourceChunkId) {
            if (sourceChunkId != null && !sourceChunkIds.contains(sourceChunkId)) {
                sourceChunkIds.add(sourceChunkId);
            }
        }

        DocumentSection toSection() {
            String sectionText = text.isEmpty() ? title : text.toString().trim();
            return new DocumentSection(level, title, sectionText, List.copyOf(sourceChunkIds));
        }
    }
}
