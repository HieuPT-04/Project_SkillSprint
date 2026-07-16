package com.skillsprint.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.MarketplaceChallengeSession;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePurchase;
import com.skillsprint.entity.MarketplaceQuizAttempt;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplacePaymentMethod;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.enums.marketplace.MarketplaceQuizAttemptType;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the shape the V7 migration establishes: one legacy item becomes one Pack
 * plus Version 1, every historical row points at that version, and no legacy row is
 * removed.
 *
 * <p>The V7 SQL itself is not executed here — the test profile runs H2 with Flyway
 * disabled, so the Postgres-only migration (jsonb, partial unique index,
 * {@code INSERT ... SELECT}) cannot run. These tests assert the resulting invariants
 * against the JPA model instead. The single-saleable-version partial index is
 * likewise Postgres-only; its service-level guard is covered in
 * {@link com.skillsprint.service.marketplace.MarketplacePackVersionServiceTest}.
 */
@DataJpaTest
@ActiveProfiles("test")
class MarketplacePackVersionRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired StudyWorkspaceRepository workspaceRepository;
    @Autowired MarketplaceItemRepository itemRepository;
    @Autowired MarketplaceQuizPackSnapshotRepository snapshotRepository;
    @Autowired MarketplacePackRepository packRepository;
    @Autowired MarketplacePackVersionRepository versionRepository;
    @Autowired MarketplacePurchaseRepository purchaseRepository;
    @Autowired MarketplaceQuizAttemptRepository attemptRepository;
    @Autowired MarketplaceReviewRepository reviewRepository;
    @Autowired MarketplaceChallengeSessionRepository sessionRepository;

    User creator;
    User buyer;
    StudyWorkspace workspace;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(user("pack-creator", "creator@example.com", "Creator"));
        buyer = userRepository.save(user("pack-buyer", "buyer@example.com", "Buyer"));
        workspace = workspaceRepository.save(workspace(creator));
    }

    @Test
    void legacyItemBecomesOnePackAndVersionOneWithItsHistoryAttached() {
        MarketplaceItem item = itemRepository.save(item(MarketplaceItemStatus.PUBLISHED));
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.save(snapshot(item));
        MarketplacePurchase purchase = purchaseRepository.save(purchase(item));
        MarketplaceQuizAttempt attempt = attemptRepository.save(attempt(item));
        MarketplaceReview review = reviewRepository.save(review(item));
        MarketplaceChallengeSession session = sessionRepository.save(session(item));

        MarketplacePackVersion version = migrate(item, snapshot);

        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getUpdateType()).isEqualTo(MarketplacePackUpdateType.MAJOR);
        assertThat(version.getStatus()).isEqualTo(MarketplacePackVersionStatus.PUBLISHED);
        assertThat(version.isSaleable()).isTrue();
        assertThat(version.getLegacyItemId()).isEqualTo(item.getItemId());
        assertThat(version.getPack().getLegacyItemId()).isEqualTo(item.getItemId());
        assertThat(version.getChapterCount()).isEqualTo(snapshot.getChapterCount());
        assertThat(version.getQuestionCount()).isEqualTo(snapshot.getQuestionCount());
        assertThat(version.getContent()).isEqualTo(snapshot.getContent());

        backfill(purchase, attempt, review, session, version);

        assertThat(purchaseRepository.findById(purchase.getPurchaseId()).orElseThrow().getPackVersion().getVersionId())
                .isEqualTo(version.getVersionId());
        assertThat(attemptRepository.findById(attempt.getAttemptId()).orElseThrow().getPackVersion().getVersionId())
                .isEqualTo(version.getVersionId());
        assertThat(reviewRepository.findById(review.getReviewId()).orElseThrow().getPackVersion().getVersionId())
                .isEqualTo(version.getVersionId());
        assertThat(sessionRepository.findById(session.getSessionId()).orElseThrow().getPackVersion().getVersionId())
                .isEqualTo(version.getVersionId());

        // The migration is additive: the legacy item, its snapshot, and every
        // historical row survive and keep pointing at the legacy item.
        assertThat(itemRepository.findById(item.getItemId())).isPresent();
        assertThat(snapshotRepository.findByItemItemId(item.getItemId())).isPresent();
        assertThat(purchaseRepository.findById(purchase.getPurchaseId()).orElseThrow()
                .getItem().getItemId()).isEqualTo(item.getItemId());
        assertThat(versionRepository.findByLegacyItemId(item.getItemId())).isPresent();
    }

    @Test
    void onlyLegacyPublishedItemBecomesSaleable() {
        MarketplaceItem draft = itemRepository.save(item(MarketplaceItemStatus.DRAFT));
        MarketplacePackVersion version = migrate(draft, snapshotRepository.save(snapshot(draft)));

        assertThat(version.getStatus()).isEqualTo(MarketplacePackVersionStatus.DRAFT);
        assertThat(version.isSaleable()).isFalse();
    }

    @Test
    void versionNoIsUniquePerPack() {
        MarketplaceItem item = itemRepository.save(item(MarketplaceItemStatus.DRAFT));
        MarketplacePackVersion first = migrate(item, snapshotRepository.save(snapshot(item)));

        MarketplacePackVersion duplicate = version(first.getPack(), 1, false);

        assertThatThrownBy(() -> versionRepository.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void differentPacksCanEachHaveOneSaleableVersion() {
        MarketplaceItem firstItem = itemRepository.save(item(MarketplaceItemStatus.PUBLISHED));
        MarketplaceItem secondItem = itemRepository.save(item(MarketplaceItemStatus.PUBLISHED));

        MarketplacePackVersion first = migrate(firstItem, snapshotRepository.save(snapshot(firstItem)));
        MarketplacePackVersion second = migrate(secondItem, snapshotRepository.save(snapshot(secondItem)));

        assertThat(first.getPack().getPackId()).isNotEqualTo(second.getPack().getPackId());
        assertThat(versionRepository.findByPackPackIdAndSaleableTrue(first.getPack().getPackId()))
                .map(MarketplacePackVersion::getVersionId).contains(first.getVersionId());
        assertThat(versionRepository.findByPackPackIdAndSaleableTrue(second.getPack().getPackId()))
                .map(MarketplacePackVersion::getVersionId).contains(second.getVersionId());
    }

    /** Mirrors what the V7 SQL does for one legacy item + snapshot pair. */
    private MarketplacePackVersion migrate(MarketplaceItem item, MarketplaceQuizPackSnapshot snapshot) {
        MarketplacePack pack = new MarketplacePack();
        pack.setCreator(creator);
        pack.setSourceWorkspace(workspace);
        pack.setLegacyItemId(item.getItemId());
        pack = packRepository.saveAndFlush(pack);

        MarketplacePackVersion version = version(pack, 1, item.getStatus() == MarketplaceItemStatus.PUBLISHED);
        version.setStatus(MarketplacePackVersionStatus.valueOf(item.getStatus().name()));
        version.setLegacyItemId(item.getItemId());
        version.setTitle(item.getTitle());
        version.setSubject(item.getSubject());
        version.setPriceCoins(item.getPriceCoins());
        version.setChapterCount(snapshot.getChapterCount());
        version.setQuizCount(snapshot.getQuizCount());
        version.setQuestionCount(snapshot.getQuestionCount());
        version.setContent(snapshot.getContent());
        version.setPublishedAt(item.getPublishedAt());
        return versionRepository.saveAndFlush(version);
    }

    private void backfill(
            MarketplacePurchase purchase,
            MarketplaceQuizAttempt attempt,
            MarketplaceReview review,
            MarketplaceChallengeSession session,
            MarketplacePackVersion version
    ) {
        purchase.setPackVersion(version);
        attempt.setPackVersion(version);
        review.setPackVersion(version);
        session.setPackVersion(version);
        purchaseRepository.saveAndFlush(purchase);
        attemptRepository.saveAndFlush(attempt);
        reviewRepository.saveAndFlush(review);
        sessionRepository.saveAndFlush(session);
    }

    private MarketplacePackVersion version(MarketplacePack pack, int versionNo, boolean saleable) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setPack(pack);
        version.setVersionNo(versionNo);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setStatus(MarketplacePackVersionStatus.DRAFT);
        version.setTitle("Pack");
        version.setSubject("Toan");
        version.setPriceCoins(100);
        version.setChapterCount(4);
        version.setQuizCount(4);
        version.setQuestionCount(20);
        version.setContent(new ObjectMapper().createObjectNode());
        version.setSaleable(saleable);
        return version;
    }

    private MarketplaceItem item(MarketplaceItemStatus status) {
        MarketplaceItem item = new MarketplaceItem();
        item.setCreator(creator);
        item.setSourceWorkspace(workspace);
        item.setTitle("Pack");
        item.setSubject("Toan");
        item.setPriceCoins(100);
        item.setStatus(status);
        item.setPublishedAt(status == MarketplaceItemStatus.PUBLISHED ? Instant.now() : null);
        return item;
    }

    private MarketplaceQuizPackSnapshot snapshot(MarketplaceItem item) {
        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setItem(item);
        snapshot.setChapterCount(4);
        snapshot.setQuizCount(4);
        snapshot.setQuestionCount(20);
        snapshot.setContent(new ObjectMapper().createObjectNode());
        return snapshot;
    }

    private MarketplacePurchase purchase(MarketplaceItem item) {
        MarketplacePurchase purchase = new MarketplacePurchase();
        purchase.setUser(buyer);
        purchase.setItem(item);
        purchase.setPriceCoins(100);
        purchase.setPaymentMethod(MarketplacePaymentMethod.COIN);
        purchase.setStatus(MarketplacePurchaseStatus.ACTIVE);
        purchase.setPurchasedAt(Instant.now());
        return purchase;
    }

    private MarketplaceQuizAttempt attempt(MarketplaceItem item) {
        MarketplaceQuizAttempt attempt = new MarketplaceQuizAttempt();
        attempt.setItem(item);
        attempt.setUser(buyer);
        attempt.setAttemptType(MarketplaceQuizAttemptType.RANKED);
        attempt.setScore(80);
        attempt.setCorrectCount(16);
        attempt.setQuestionCount(20);
        attempt.setDurationSeconds(300L);
        attempt.setSuspicious(false);
        attempt.setCompletedAt(Instant.now());
        return attempt;
    }

    private MarketplaceReview review(MarketplaceItem item) {
        MarketplaceReview review = new MarketplaceReview();
        review.setItem(item);
        review.setUser(buyer);
        review.setRating(5);
        review.setComment("Tot");
        return review;
    }

    private MarketplaceChallengeSession session(MarketplaceItem item) {
        MarketplaceChallengeSession session = new MarketplaceChallengeSession();
        session.setItem(item);
        session.setUser(buyer);
        session.setStartedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        return session;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(user);
        workspace.setName("Java");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId, String email, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        return user;
    }
}
