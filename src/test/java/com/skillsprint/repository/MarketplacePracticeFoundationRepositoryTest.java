package com.skillsprint.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePracticeAttempt;
import com.skillsprint.entity.MarketplaceVersionProgress;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplacePracticeAttemptStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MarketplacePracticeFoundationRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired StudyWorkspaceRepository workspaceRepository;
    @Autowired MarketplacePackRepository packRepository;
    @Autowired MarketplacePackVersionRepository versionRepository;
    @Autowired MarketplacePracticeAttemptRepository attemptRepository;
    @Autowired MarketplaceVersionProgressRepository progressRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User buyer;
    private MarketplacePackVersion version;

    @BeforeEach
    void setUp() {
        User creator = userRepository.save(user("practice-creator", "practice-creator@example.com"));
        buyer = userRepository.save(user("practice-buyer", "practice-buyer@example.com"));

        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(creator);
        workspace.setName("Practice source");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        workspace = workspaceRepository.save(workspace);

        MarketplacePack pack = new MarketplacePack();
        pack.setCreator(creator);
        pack.setSourceWorkspace(workspace);
        pack = packRepository.save(pack);

        version = new MarketplacePackVersion();
        version.setPack(pack);
        version.setVersionNo(1);
        version.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setTitle("Practice Pack");
        version.setSubject("Java");
        version.setPriceCoins(100);
        version.setChapterCount(2);
        version.setQuizCount(2);
        version.setQuestionCount(10);
        version.setContent(objectMapper.createObjectNode());
        version.setSaleable(true);
        version = versionRepository.saveAndFlush(version);
    }

    @Test
    void persistsAndFindsActiveAttemptAndHistory() {
        MarketplacePracticeAttempt attempt = attempt(MarketplacePracticeAttemptStatus.IN_PROGRESS, 1);
        attempt = attemptRepository.saveAndFlush(attempt);

        assertThat(attemptRepository
                .findByBuyerUserIdAndPackVersionVersionIdAndChapterSequenceNoAndStatus(
                        buyer.getUserId(), version.getVersionId(), 1,
                        MarketplacePracticeAttemptStatus.IN_PROGRESS))
                .map(MarketplacePracticeAttempt::getAttemptId)
                .contains(attempt.getAttemptId());
        assertThat(attemptRepository
                .findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc(
                        buyer.getUserId(), version.getVersionId()))
                .extracting(MarketplacePracticeAttempt::getAttemptId)
                .containsExactly(attempt.getAttemptId());
    }

    @Test
    void storesVersionScopedProgressAndRejectsDuplicateAggregate() {
        MarketplaceVersionProgress first = progress();
        progressRepository.saveAndFlush(first);

        assertThat(progressRepository.findByBuyerUserIdAndPackVersionVersionId(
                buyer.getUserId(), version.getVersionId()))
                .get()
                .extracting(MarketplaceVersionProgress::getCompletionPercent)
                .isEqualTo(new BigDecimal("50.00"));

        assertThatThrownBy(() -> progressRepository.saveAndFlush(progress()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void summarizesCompletedAttemptsByDistinctChapter() {
        MarketplacePracticeAttempt first = completedAttempt(1, 60, Instant.parse("2026-07-18T01:00:00Z"));
        MarketplacePracticeAttempt retry = completedAttempt(1, 90, Instant.parse("2026-07-18T02:00:00Z"));
        MarketplacePracticeAttempt secondChapter = completedAttempt(2, 80, Instant.parse("2026-07-18T03:00:00Z"));
        attemptRepository.saveAllAndFlush(java.util.List.of(first, retry, secondChapter));

        var summaries = attemptRepository.summarizeCompletedChapters(
                buyer.getUserId(),
                version.getVersionId(),
                MarketplacePracticeAttemptStatus.COMPLETED
        );

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).getChapterSequenceNo()).isEqualTo(1);
        assertThat(summaries.get(0).getBestScore()).isEqualTo(90);
        assertThat(summaries.get(0).getAttemptCount()).isEqualTo(2);
        assertThat(summaries.get(0).getLastCompletedAt()).isEqualTo(Instant.parse("2026-07-18T02:00:00Z"));
    }

    private MarketplacePracticeAttempt attempt(MarketplacePracticeAttemptStatus status, int chapterSequenceNo) {
        MarketplacePracticeAttempt attempt = new MarketplacePracticeAttempt();
        attempt.setBuyer(buyer);
        attempt.setPackVersion(version);
        attempt.setChapterSequenceNo(chapterSequenceNo);
        attempt.setStatus(status);
        attempt.setQuestionSnapshot(objectMapper.createArrayNode());
        attempt.setAnswerSnapshot(objectMapper.createObjectNode());
        attempt.setQuestionCount(5);
        attempt.setStartedAt(Instant.now());
        return attempt;
    }

    private MarketplacePracticeAttempt completedAttempt(int chapterSequenceNo, int score, Instant completedAt) {
        MarketplacePracticeAttempt attempt = attempt(MarketplacePracticeAttemptStatus.COMPLETED, chapterSequenceNo);
        attempt.setScore(score);
        attempt.setCorrectCount(Math.round(score / 20.0f));
        attempt.setCompletedAt(completedAt);
        return attempt;
    }

    private MarketplaceVersionProgress progress() {
        MarketplaceVersionProgress progress = new MarketplaceVersionProgress();
        progress.setBuyer(buyer);
        progress.setPackVersion(version);
        progress.setCompletedQuizCount(1);
        progress.setCompletedChapterCount(1);
        progress.setCompletionPercent(new BigDecimal("50.00"));
        progress.setFirstActivityAt(Instant.now());
        progress.setLastActivityAt(Instant.now());
        return progress;
    }

    private User user(String userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(userId);
        return user;
    }
}
