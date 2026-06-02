package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.StudySession;
import com.skillsprint.enums.session.StudySessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    List<StudySession> findByWorkspaceWorkspaceId(UUID workspaceId);

    List<StudySession> findByUserUserId(String userId);

    List<StudySession> findByWorkspaceWorkspaceIdAndUserUserId(UUID workspaceId, String userId);

    Optional<StudySession> findFirstByCalendarTaskTaskIdAndUserUserIdAndStatus(
            UUID taskId,
            String userId,
            StudySessionStatus status
    );

    Optional<StudySession> findFirstByWorkspaceWorkspaceIdAndUserUserIdAndStatusOrderByStartedAtDesc(
            UUID workspaceId,
            String userId,
            StudySessionStatus status
    );

    long countByStatus(StudySessionStatus status);
}
