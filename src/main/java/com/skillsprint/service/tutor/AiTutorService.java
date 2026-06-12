package com.skillsprint.service.tutor;

import com.skillsprint.configuration.ai.GeminiProperties;
import com.skillsprint.dto.request.tutor.TutorAskRequest;
import com.skillsprint.dto.response.tutor.TutorAskResponse;
import com.skillsprint.dto.response.tutor.TutorContextResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.Roadmap;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.RoadmapStepRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.tutor.ai.AiTutorDraft;
import com.skillsprint.service.tutor.ai.GeminiTutorClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiTutorService {

    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int MAX_CHUNKS = 8;
    private static final int MAX_STEPS_IN_CONTEXT = 12;
    private static final int MAX_TODAY_TASKS = 8;
    private static final int MIN_CONTEXT_LENGTH = 30;
    private static final int MAX_ANSWER_LENGTH = 480;

    RoadmapStepRepository roadmapStepRepository;
    RoadmapRepository roadmapRepository;
    StudyWorkspaceRepository workspaceRepository;
    MaterialChunkRepository materialChunkRepository;
    CalendarTaskRepository calendarTaskRepository;
    GeminiTutorClient geminiTutorClient;
    GeminiProperties geminiProperties;
    QuotaService quotaService;

    @Transactional(readOnly = true)
    public TutorAskResponse askWorkspace(String userId, UUID workspaceId, TutorAskRequest request) {
        quotaService.validateFeature(userId, PlanFeatureKeys.AI_TUTOR);
        String question = request == null ? null : request.getQuestion();
        validateQuestion(question);

        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        List<Roadmap> roadmaps = roadmapRepository.findByWorkspaceWorkspaceId(workspaceId);
        Roadmap activeRoadmap = pickActiveRoadmap(roadmaps);
        List<RoadmapStep> accessibleSteps = findAccessibleSteps(userId, workspaceId, activeRoadmap);
        RoadmapStep matchedStep = matchStep(question, activeRoadmap, accessibleSteps);

        List<CalendarTask> todayTasks = calendarTaskRepository
                .findByWorkspaceWorkspaceIdAndUserUserIdAndTaskDateOrderByStartTimeAscCreatedAtAsc(
                        workspaceId,
                        userId,
                        LocalDate.now()
                );
        List<MaterialChunk> chunks = materialChunkRepository
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspaceId);

        String context = buildWorkspaceContext(workspace, activeRoadmap, accessibleSteps, matchedStep, todayTasks, chunks, question);
        if (context.length() < MIN_CONTEXT_LENGTH) {
            throw new AppException(ErrorCode.TUTOR_CONTEXT_NOT_READY);
        }

        AiTutorDraft draft = geminiTutorClient.ask(question.trim(), context);
        if (isValidDraft(draft)) {
            return buildResponse(matchedStep, workspace, "WORKSPACE", draft);
        }

        return buildWorkspaceFallbackResponse(workspace, matchedStep);
    }

    @Transactional(readOnly = true)
    public TutorAskResponse ask(String userId, UUID stepId, TutorAskRequest request) {
        quotaService.validateFeature(userId, PlanFeatureKeys.AI_TUTOR);
        String question = request == null ? null : request.getQuestion();
        validateQuestion(question);

        RoadmapStep step = findOwnedStep(userId, stepId);
        quotaService.validateCanAccessRoadmapStep(userId, step);

        List<MaterialChunk> chunks = materialChunkRepository
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(step.getWorkspace().getWorkspaceId());
        String context = buildContext(step, chunks);
        if (context.length() < MIN_CONTEXT_LENGTH) {
            throw new AppException(ErrorCode.TUTOR_CONTEXT_NOT_READY);
        }

        AiTutorDraft draft = geminiTutorClient.ask(question.trim(), context);
        if (isValidDraft(draft)) {
            return buildResponse(step, step.getWorkspace(), "ROADMAP_STEP", draft);
        }

        return buildFallbackResponse(step);
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new AppException(ErrorCode.TUTOR_QUESTION_REQUIRED);
        }
        if (question.trim().length() > MAX_QUESTION_LENGTH) {
            throw new AppException(ErrorCode.TUTOR_QUESTION_TOO_LONG);
        }
    }

    private RoadmapStep findOwnedStep(String userId, UUID stepId) {
        return roadmapStepRepository.findById(stepId)
                .filter(step -> step.getWorkspace().getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.ROADMAP_NOT_FOUND));
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        com.skillsprint.enums.workspace.WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private Roadmap pickActiveRoadmap(List<Roadmap> roadmaps) {
        if (roadmaps == null || roadmaps.isEmpty()) {
            return null;
        }
        return roadmaps.stream()
                .filter(roadmap -> roadmap.getStatus() == com.skillsprint.enums.roadmap.RoadmapStatus.ACTIVE)
                .max(Comparator.comparing(Roadmap::getVersionNo, Comparator.nullsLast(Integer::compareTo)))
                .orElseGet(() -> roadmaps.stream()
                        .max(Comparator.comparing(Roadmap::getVersionNo, Comparator.nullsLast(Integer::compareTo)))
                        .orElse(null));
    }

    private List<RoadmapStep> findAccessibleSteps(String userId, UUID workspaceId, Roadmap roadmap) {
        List<RoadmapStep> steps = roadmap == null
                ? roadmapStepRepository.findByWorkspaceWorkspaceId(workspaceId)
                : roadmapStepRepository.findByRoadmapRoadmapIdOrderBySequenceNoAsc(roadmap.getRoadmapId());
        int unlockedLimit = quotaService.getUnlockedRoadmapStepLimit(userId);
        return steps.stream()
                .filter(step -> step.getSequenceNo() == null || step.getSequenceNo() <= unlockedLimit)
                .sorted(Comparator.comparing(RoadmapStep::getSequenceNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private RoadmapStep matchStep(String question, Roadmap roadmap, List<RoadmapStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }

        Set<String> keywords = new LinkedHashSet<>();
        addWords(keywords, question);
        if (keywords.isEmpty()) {
            return pickCurrentAccessibleStep(roadmap, steps);
        }

        return steps.stream()
                .max(Comparator
                        .comparingInt((RoadmapStep step) -> scoreStep(step, keywords))
                        .thenComparing(RoadmapStep::getSequenceNo, Comparator.nullsLast(Integer::compareTo)))
                .filter(step -> scoreStep(step, keywords) > 0)
                .orElseGet(() -> pickCurrentAccessibleStep(roadmap, steps));
    }

    private RoadmapStep pickCurrentAccessibleStep(Roadmap roadmap, List<RoadmapStep> steps) {
        if (roadmap != null && roadmap.getCurrentStep() != null) {
            UUID currentStepId = roadmap.getCurrentStep().getStepId();
            return steps.stream()
                    .filter(step -> step.getStepId().equals(currentStepId))
                    .findFirst()
                    .orElse(steps.get(0));
        }
        return steps.get(0);
    }

    private String buildWorkspaceContext(
            StudyWorkspace workspace,
            Roadmap roadmap,
            List<RoadmapStep> steps,
            RoadmapStep matchedStep,
            List<CalendarTask> todayTasks,
            List<MaterialChunk> chunks,
            String question
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Workspace\n")
                .append("Name: ").append(safe(workspace.getName())).append('\n')
                .append("Description: ").append(safe(workspace.getDescription())).append('\n');

        if (roadmap != null) {
            builder.append("\nCurrent roadmap\n")
                    .append("Title: ").append(safe(roadmap.getTitle())).append('\n')
                    .append("Description: ").append(safe(roadmap.getDescription())).append('\n')
                    .append("Progress: ").append(roadmap.getProgressPercent()).append("%\n");
        }

        if (matchedStep != null) {
            builder.append("\nMost relevant step\n");
            appendStepContext(builder, matchedStep);
        }

        if (steps != null && !steps.isEmpty()) {
            builder.append("\nAccessible roadmap steps\n");
            steps.stream()
                    .limit(MAX_STEPS_IN_CONTEXT)
                    .forEach(step -> builder
                            .append("- ")
                            .append(step.getSequenceNo())
                            .append(". ")
                            .append(safe(step.getTitle()))
                            .append(": ")
                            .append(safe(step.getSummary()))
                            .append('\n'));
        }

        if (todayTasks != null && !todayTasks.isEmpty()) {
            builder.append("\nToday calendar tasks\n");
            todayTasks.stream()
                    .limit(MAX_TODAY_TASKS)
                    .forEach(task -> builder
                            .append("- ")
                            .append(safe(task.getTitle()))
                            .append(" | status: ")
                            .append(task.getStatus())
                            .append(" | time: ")
                            .append(task.getStartTime())
                            .append("-")
                            .append(task.getEndTime())
                            .append('\n'));
        }

        List<MaterialChunk> selectedChunks = selectRelevantChunks(question, matchedStep, chunks);
        appendChunks(builder, selectedChunks);

        String context = builder.toString();
        return context.length() > geminiProperties.inputLimit()
                ? context.substring(0, geminiProperties.inputLimit())
                : context;
    }

    private String buildContext(RoadmapStep step, List<MaterialChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        builder.append("Roadmap step\n");
        appendStepContext(builder, step);

        List<MaterialChunk> selectedChunks = selectRelevantChunks(step, chunks);
        appendChunks(builder, selectedChunks);

        String context = builder.toString();
        return context.length() > geminiProperties.inputLimit()
                ? context.substring(0, geminiProperties.inputLimit())
                : context;
    }

    private void appendStepContext(StringBuilder builder, RoadmapStep step) {
        builder.append("Title: ").append(safe(step.getTitle())).append('\n')
                .append("Subtitle: ").append(safe(step.getSubtitle())).append('\n')
                .append("Summary: ").append(safe(step.getSummary())).append('\n')
                .append("What to learn: ").append(toLine(step.getWhatToLearn())).append('\n')
                .append("Key concepts: ").append(toLine(step.getKeyConcepts())).append('\n')
                .append("Learning outcomes: ").append(toLine(step.getLearningOutcomes())).append('\n')
                .append("Recommended focus: ").append(toLine(step.getRecommendedFocus())).append('\n');
    }

    private void appendChunks(StringBuilder builder, List<MaterialChunk> selectedChunks) {
        for (MaterialChunk chunk : selectedChunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            builder.append("\nMaterial chunk: ")
                    .append(safe(chunk.getSectionTitle()))
                    .append('\n')
                    .append(chunk.getContent())
                    .append('\n');
            if (builder.length() >= geminiProperties.inputLimit()) {
                break;
            }
        }
    }

    private List<MaterialChunk> selectRelevantChunks(RoadmapStep step, List<MaterialChunk> chunks) {
        return selectRelevantChunks(null, step, chunks);
    }

    private List<MaterialChunk> selectRelevantChunks(String question, RoadmapStep step, List<MaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Set<String> keywords = buildKeywords(question, step);
        return chunks.stream()
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank())
                .sorted(Comparator
                        .comparingInt((MaterialChunk chunk) -> scoreChunk(chunk, keywords))
                        .reversed()
                        .thenComparing(MaterialChunk::getChunkIndex, Comparator.nullsLast(Integer::compareTo)))
                .limit(MAX_CHUNKS)
                .toList();
    }

    private Set<String> buildKeywords(RoadmapStep step) {
        return buildKeywords(null, step);
    }

    private Set<String> buildKeywords(String question, RoadmapStep step) {
        Set<String> keywords = new LinkedHashSet<>();
        addWords(keywords, question);
        if (step == null) {
            return keywords;
        }
        addWords(keywords, step.getTitle());
        addWords(keywords, step.getSubtitle());
        addWords(keywords, step.getSummary());
        addWords(keywords, toLine(step.getKeyConcepts()));
        addWords(keywords, toLine(step.getLearningOutcomes()));
        return keywords;
    }

    private int scoreStep(RoadmapStep step, Set<String> keywords) {
        String text = (safe(step.getTitle()) + " "
                + safe(step.getSubtitle()) + " "
                + safe(step.getSummary()) + " "
                + toLine(step.getKeyConcepts()) + " "
                + toLine(step.getLearningOutcomes())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private void addWords(Set<String> keywords, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String word : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 4) {
                keywords.add(word);
            }
        }
    }

    private int scoreChunk(MaterialChunk chunk, Set<String> keywords) {
        String text = (safe(chunk.getSectionTitle()) + " " + safe(chunk.getSummary()) + " " + safe(chunk.getContent()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private boolean isValidDraft(AiTutorDraft draft) {
        return draft != null
                && draft.answer() != null
                && !draft.answer().isBlank()
                && draft.suggestedQuestions() != null
                && !draft.suggestedQuestions().isEmpty();
    }

    private TutorAskResponse buildResponse(RoadmapStep step, StudyWorkspace workspace, String contextType, AiTutorDraft draft) {
        String confidence = normalizeConfidence(draft.confidence(), step, contextType);
        return TutorAskResponse.builder()
                .answer(compactAnswer(draft.answer()))
                .suggestedQuestions(buildSuggestions(draft.suggestedQuestions(), step, contextType, confidence))
                .confidence(confidence)
                .context(toContext(step, workspace, contextType))
                .build();
    }

    private TutorAskResponse buildFallbackResponse(RoadmapStep step) {
        return TutorAskResponse.builder()
                .answer("AI Tutor chưa sẵn sàng hoặc chưa trả lời được câu này. Bạn có thể xem lại phần \""
                        + safe(step.getTitle())
                        + "\" và tập trung vào các ý chính trong key concepts.")
                .suggestedQuestions(defaultSuggestions(step))
                .confidence("LOW")
                .context(toContext(step, step.getWorkspace(), "ROADMAP_STEP"))
                .build();
    }

    private TutorAskResponse buildWorkspaceFallbackResponse(StudyWorkspace workspace, RoadmapStep matchedStep) {
        String subject = matchedStep == null ? safe(workspace.getName()) : safe(matchedStep.getTitle());
        return TutorAskResponse.builder()
                .answer("AI Tutor chưa sẵn sàng hoặc chưa trả lời được câu này. Bạn có thể hỏi cụ thể hơn về \""
                        + truncate(subject, 80)
                        + "\" hoặc xem lại roadmap/tài liệu trong workspace.")
                .suggestedQuestions(defaultWorkspaceSuggestions(matchedStep))
                .confidence("LOW")
                .context(toContext(matchedStep, workspace, "WORKSPACE"))
                .build();
    }

    private String compactAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String value = answer.trim()
                .replace("**", "")
                .replace("__", "");
        value = value.replaceAll("(?m)^\\s*[-*•]\\s*", "");
        value = value.replaceAll("(?m)^\\s*\\d+[.)]\\s*", "");
        value = value.replaceAll("\\s*\\n+\\s*", " ");
        value = value.replaceAll("\\s{2,}", " ").trim();
        return truncate(value, MAX_ANSWER_LENGTH);
    }

    private List<String> compactSuggestions(List<String> suggestions, RoadmapStep step) {
        List<String> result = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (suggestion != null && !suggestion.isBlank()) {
                result.add(truncate(suggestion.trim(), 80));
            }
            if (result.size() == 3) {
                break;
            }
        }
        if (result.size() < 3) {
            result.addAll(defaultSuggestions(step));
        }
        return result.stream().distinct().limit(3).toList();
    }

    private List<String> buildSuggestions(
            List<String> suggestions,
            RoadmapStep step,
            String contextType,
            String confidence
    ) {
        if ("LOW".equals(confidence)) {
            return "WORKSPACE".equals(contextType) ? defaultWorkspaceSuggestions(step) : defaultSuggestions(step);
        }
        return compactSuggestions(suggestions, step);
    }

    private List<String> defaultSuggestions(RoadmapStep step) {
        String title = step == null ? "workspace này" : safe(step.getTitle());
        return List.of(
                "Phần \"" + truncate(title, 60) + "\" cần học gì?",
                "Khái niệm chính của bài này là gì?",
                "Cho tôi một ví dụ dễ hiểu hơn"
        );
    }

    private List<String> defaultWorkspaceSuggestions(RoadmapStep matchedStep) {
        if (matchedStep != null) {
            return defaultSuggestions(matchedStep);
        }
        return List.of(
                "Hôm nay tôi nên học gì?",
                "Tóm tắt roadmap hiện tại cho tôi",
                "Tôi nên ôn phần nào trước?"
        );
    }

    private String normalizeConfidence(String confidence, RoadmapStep matchedStep, String contextType) {
        if (confidence == null || confidence.isBlank()) {
            return "MEDIUM";
        }
        String value = confidence.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("HIGH") && !value.equals("MEDIUM") && !value.equals("LOW")) {
            return "MEDIUM";
        }
        if ("WORKSPACE".equals(contextType) && matchedStep == null && value.equals("HIGH")) {
            return "MEDIUM";
        }
        return value;
    }

    private TutorContextResponse toContext(RoadmapStep step, StudyWorkspace workspace, String contextType) {
        return TutorContextResponse.builder()
                .scope(contextType)
                .workspaceId(workspace.getWorkspaceId())
                .workspaceName(workspace.getName())
                .matchedStepId(step == null ? null : step.getStepId())
                .matchedStepTitle(step == null ? null : step.getTitle())
                .build();
    }

    private String toLine(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("; ", values);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }
}
