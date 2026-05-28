package com.skillsprint.service.session;

import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.StudySession;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.StudySessionMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.service.calendar.CalendarService;
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

    CalendarTaskRepository calendarTaskRepository;
    StudySessionRepository studySessionRepository;
    StudySessionMapper studySessionMapper;
    CalendarService calendarService;

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
