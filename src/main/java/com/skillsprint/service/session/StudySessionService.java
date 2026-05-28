package com.skillsprint.service.session;

import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.roadmap.RoadmapResourceResponse;
import com.skillsprint.dto.response.session.StudySessionDetailResponse;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.RoadmapStep;
import com.skillsprint.entity.RoadmapStepResource;
import com.skillsprint.entity.StudySession;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.CalendarMapper;
import com.skillsprint.mapper.RoadmapMapper;
import com.skillsprint.mapper.StudySessionMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.RoadmapStepResourceRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.service.calendar.CalendarService;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StudySessionService {

    private static final int TITLE_LENGTH = 120;
    private static final int SUMMARY_LENGTH = 700;
    private static final int LIST_ITEM_LENGTH = 120;
    private static final int MAX_LIST_ITEMS = 6;

    CalendarTaskRepository calendarTaskRepository;
    RoadmapStepResourceRepository roadmapStepResourceRepository;
    StudySessionRepository studySessionRepository;
    CalendarMapper calendarMapper;
    RoadmapMapper roadmapMapper;
    StudySessionMapper studySessionMapper;
    CalendarService calendarService;

    @Transactional(readOnly = true)
    public StudySessionDetailResponse getStudySessionDetail(String userId, UUID taskId) {
        CalendarTask task = findOwnedTask(userId, taskId);
        RoadmapStep step = task.getRoadmapStep();
        if (step == null) {
            throw new AppException(ErrorCode.ROADMAP_NOT_FOUND);
        }

        List<RoadmapStepResource> resources = roadmapStepResourceRepository
                .findByStepStepIdOrderBySequenceNoAsc(step.getStepId());
        return StudySessionDetailResponse.builder()
                .task(calendarMapper.toTaskResponse(task))
                .roadmapStep(toRoadmapStepStudyResponse(step))
                .practice(buildPracticePrompt(step))
                .resources(resources.stream().map(roadmapMapper::toResourceResponse).toList())
                .actions(buildActions(task))
                .build();
    }

    @Transactional
    public StudySessionResponse startSession(String userId, UUID taskId) {
        CalendarTask task = findOwnedTask(userId, taskId);
        if (task.getStatus() == CalendarTaskStatus.COMPLETED) {
            throw new AppException(ErrorCode.STUDY_SESSION_TASK_ALREADY_COMPLETED);
        }

        StudySession session = studySessionRepository
                .findFirstByCalendarTaskTaskIdAndUserUserIdAndStatus(
                        taskId,
                        userId,
                        StudySessionStatus.IN_PROGRESS
                )
                .orElseGet(() -> createSession(task));

        return studySessionMapper.toResponse(studySessionRepository.save(session));
    }

    @Transactional
    public StudySessionResponse finishSession(
            String userId,
            UUID sessionId,
            FinishStudySessionRequest request
    ) {
        StudySession session = studySessionRepository.findById(sessionId)
                .filter(studySession -> studySession.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.STUDY_SESSION_NOT_FOUND));

        if (session.getStatus() != StudySessionStatus.COMPLETED) {
            Instant endedAt = Instant.now();
            session.setEndedAt(endedAt);
            session.setDurationMinutes(calculateDurationMinutes(session.getStartedAt(), endedAt));
            session.setStatus(StudySessionStatus.COMPLETED);
            session.setNotes(request == null ? null : request.getNotes());
            session.setFocusScore(request == null ? null : request.getFocusScore());
            studySessionRepository.save(session);

            if (session.getCalendarTask() != null) {
                calendarService.completeTask(userId, session.getCalendarTask().getTaskId());
            }
        }

        return studySessionMapper.toResponse(session);
    }

    private StudySessionDetailResponse.RoadmapStepStudyResponse toRoadmapStepStudyResponse(RoadmapStep step) {
        return StudySessionDetailResponse.RoadmapStepStudyResponse.builder()
                .stepId(step.getStepId())
                .chapterId(step.getChapter() == null ? null : step.getChapter().getChapterId())
                .topicId(step.getTopic() == null ? null : step.getTopic().getTopicId())
                .title(truncate(step.getTitle(), TITLE_LENGTH))
                .subtitle(truncate(step.getSubtitle(), TITLE_LENGTH))
                .summary(truncate(step.getSummary(), SUMMARY_LENGTH))
                .whatToLearn(compactList(step.getWhatToLearn()))
                .keyConcepts(compactList(step.getKeyConcepts()))
                .learningOutcomes(compactList(step.getLearningOutcomes()))
                .recommendedFocus(compactList(step.getRecommendedFocus()))
                .difficulty(step.getDifficulty())
                .estimatedMinutes(step.getEstimatedMinutes())
                .sequenceNo(step.getSequenceNo())
                .status(step.getStatus())
                .build();
    }

    private StudySessionDetailResponse.PracticePromptResponse buildPracticePrompt(RoadmapStep step) {
        String mainConcept = step.getKeyConcepts() == null || step.getKeyConcepts().isEmpty()
                ? step.getTitle()
                : step.getKeyConcepts().get(0);
        return StudySessionDetailResponse.PracticePromptResponse.builder()
                .prompt("Hãy học phần \"" + step.getTitle() + "\" và tự giải thích lại: " + mainConcept + ".")
                .expectedOutput("Bạn nắm được ý chính và có thể trình bày lại bằng ví dụ ngắn.")
                .build();
    }

    private StudySessionDetailResponse.StudySessionActionsResponse buildActions(CalendarTask task) {
        boolean completed = task.getStatus() == CalendarTaskStatus.COMPLETED;
        String taskId = task.getTaskId().toString();
        return StudySessionDetailResponse.StudySessionActionsResponse.builder()
                .canStart(!completed)
                .canFinish(!completed)
                .canCompleteTask(!completed)
                .startEndpoint("/api/calendar/tasks/" + taskId + "/sessions/start")
                .finishEndpointTemplate("/api/study-sessions/{sessionId}/finish")
                .completeTaskEndpoint("/api/calendar/tasks/" + taskId + "/complete")
                .build();
    }

    private List<String> compactList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_LIST_ITEMS)
                .map(value -> truncate(value, LIST_ITEM_LENGTH))
                .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3).trim() + "...";
    }

    private CalendarTask findOwnedTask(String userId, UUID taskId) {
        return calendarTaskRepository.findById(taskId)
                .filter(task -> task.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_TASK_NOT_FOUND));
    }

    private StudySession createSession(CalendarTask task) {
        StudySession session = new StudySession();
        session.setWorkspace(task.getWorkspace());
        session.setCalendarTask(task);
        session.setRoadmapStep(task.getRoadmapStep());
        session.setUser(task.getUser());
        session.setStatus(StudySessionStatus.IN_PROGRESS);
        return session;
    }

    private int calculateDurationMinutes(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            return 0;
        }
        return Math.max(1, (int) Duration.between(startedAt, endedAt).toMinutes());
    }
}
