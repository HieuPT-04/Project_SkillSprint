package com.skillsprint.service.points;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.PointEvent;
import com.skillsprint.entity.Quiz;
import com.skillsprint.entity.QuizAttempt;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserPointSummary;
import com.skillsprint.entity.UserQuizScore;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import com.skillsprint.enums.quiz.QuizAttemptStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.repository.PointEventRepository;
import com.skillsprint.repository.UserPointSummaryRepository;
import com.skillsprint.repository.UserQuizScoreRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    PointEventRepository pointEventRepository;

    @Mock
    UserPointSummaryRepository userPointSummaryRepository;

    @Mock
    UserQuizScoreRepository userQuizScoreRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    S3PresignedUrlService s3PresignedUrlService;

    PointService pointService;
    User user;
    StudyWorkspace workspace;

    @BeforeEach
    void setUp() {
        pointService = new PointService(
                pointEventRepository,
                userPointSummaryRepository,
                userQuizScoreRepository,
                userRepository,
                s3PresignedUrlService
        );
        user = user("user-1");
        workspace = workspace(user);
    }

    @Test
    void awardRoadmapStepCompletedWritesUniqueEventAndUpdatesSummary() {
        UUID stepId = UUID.randomUUID();
        when(pointEventRepository.existsByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
                "user-1",
                PointEventType.ROADMAP_STEP_COMPLETED,
                PointSourceType.ROADMAP_STEP,
                stepId.toString()
        )).thenReturn(false);
        when(userPointSummaryRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        pointService.awardRoadmapStepCompleted(user, workspace, stepId);

        PointEvent event = captureEvent();
        assertSame(user, event.getUser());
        assertSame(workspace, event.getWorkspace());
        assertEquals(PointEventType.ROADMAP_STEP_COMPLETED, event.getEventType());
        assertEquals(PointSourceType.ROADMAP_STEP, event.getSourceType());
        assertEquals(stepId.toString(), event.getSourceId());
        assertEquals(120, event.getPoints());

        UserPointSummary summary = captureSummary();
        assertEquals(120, summary.getTotalPoints());
        assertEquals(120, summary.getCurrentWeekPoints());
        assertEquals(120, summary.getCurrentMonthPoints());
        assertEquals(1, summary.getStreakDays());
    }

    @Test
    void awardRoadmapStepCompletedSkipsDuplicateSourceEvent() {
        UUID stepId = UUID.randomUUID();
        when(pointEventRepository.existsByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
                "user-1",
                PointEventType.ROADMAP_STEP_COMPLETED,
                PointSourceType.ROADMAP_STEP,
                stepId.toString()
        )).thenReturn(true);

        pointService.awardRoadmapStepCompleted(user, workspace, stepId);

        verify(pointEventRepository, never()).saveAndFlush(any());
        verify(userPointSummaryRepository, never()).save(any());
    }

    @Test
    void awardQuizScoreWritesUpgradeBonusOnlyForPointDelta() {
        Quiz quiz = quiz();
        QuizAttempt attempt = attempt(quiz, 95);
        UserQuizScore existingScore = new UserQuizScore();
        existingScore.setUser(user);
        existingScore.setQuiz(quiz);
        existingScore.setBestScorePercent(75);
        existingScore.setEarnedPoints(80);
        when(userQuizScoreRepository.findByUserUserIdAndQuizQuizId("user-1", quiz.getQuizId()))
                .thenReturn(Optional.of(existingScore));
        when(pointEventRepository.existsByUserUserIdAndEventTypeAndSourceTypeAndSourceId(
                "user-1",
                PointEventType.QUIZ_UPGRADE_BONUS,
                PointSourceType.QUIZ,
                quiz.getQuizId().toString()
        )).thenReturn(false);
        when(userPointSummaryRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        pointService.awardQuizScore(quiz, attempt);

        PointEvent event = captureEvent();
        assertEquals(PointEventType.QUIZ_UPGRADE_BONUS, event.getEventType());
        assertEquals(PointSourceType.QUIZ, event.getSourceType());
        assertEquals(40, event.getPoints());
        assertSame(attempt, existingScore.getBestAttempt());
        assertEquals(95, existingScore.getBestScorePercent());
        assertEquals(120, existingScore.getEarnedPoints());
        verify(userQuizScoreRepository).saveAndFlush(existingScore);
    }

    @Test
    void awardQuizScoreBelowPassingOnlyRecordsBestAttemptWithoutPoints() {
        Quiz quiz = quiz();
        QuizAttempt attempt = attempt(quiz, 60);
        when(userQuizScoreRepository.findByUserUserIdAndQuizQuizId("user-1", quiz.getQuizId()))
                .thenReturn(Optional.empty());

        pointService.awardQuizScore(quiz, attempt);

        verify(pointEventRepository, never()).saveAndFlush(any());
        ArgumentCaptor<UserQuizScore> scoreCaptor = ArgumentCaptor.forClass(UserQuizScore.class);
        verify(userQuizScoreRepository).save(scoreCaptor.capture());
        assertSame(attempt, scoreCaptor.getValue().getBestAttempt());
        assertEquals(60, scoreCaptor.getValue().getBestScorePercent());
        assertEquals(0, scoreCaptor.getValue().getEarnedPoints());
    }

    private PointEvent captureEvent() {
        ArgumentCaptor<PointEvent> captor = ArgumentCaptor.forClass(PointEvent.class);
        verify(pointEventRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private UserPointSummary captureSummary() {
        ArgumentCaptor<UserPointSummary> captor = ArgumentCaptor.forClass(UserPointSummary.class);
        verify(userPointSummaryRepository).save(captor.capture());
        return captor.getValue();
    }

    private QuizAttempt attempt(Quiz quiz, int score) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAttemptId(UUID.randomUUID());
        attempt.setQuiz(quiz);
        attempt.setUser(user);
        attempt.setScore(score);
        attempt.setPassed(score >= 70);
        attempt.setCorrectCount(score >= 70 ? 5 : 3);
        attempt.setQuestionCount(5);
        attempt.setStatus(score >= 70 ? QuizAttemptStatus.PASSED : QuizAttemptStatus.FAILED);
        attempt.setSubmittedAt(Instant.parse("2026-06-23T10:00:00Z"));
        return attempt;
    }

    private Quiz quiz() {
        Quiz quiz = new Quiz();
        quiz.setQuizId(UUID.randomUUID());
        quiz.setUser(user);
        quiz.setWorkspace(workspace);
        quiz.setTitle("Quiz");
        return quiz;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName("Java");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
