package com.skillsprint.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the review repository's canonical Pack Version scope. The test profile
 * uses Hibernate-created H2 tables, so PostgreSQL partial indexes from V19 are
 * reviewed in migration SQL while the repository queries are executed here.
 */
@DataJpaTest
@ActiveProfiles("test")
class MarketplaceReviewRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired StudyWorkspaceRepository workspaceRepository;
    @Autowired MarketplaceItemRepository itemRepository;
    @Autowired MarketplacePackRepository packRepository;
    @Autowired MarketplacePackVersionRepository versionRepository;
    @Autowired MarketplaceReviewRepository reviewRepository;

    User creator;
    User buyer;
    User secondBuyer;
    StudyWorkspace workspace;
    MarketplacePack pack;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(user("review-creator", "creator-review@example.com", "Creator"));
        buyer = userRepository.save(user("review-buyer", "buyer-review@example.com", "Buyer"));
        secondBuyer = userRepository.save(user(
                "review-second-buyer",
                "second-buyer-review@example.com",
                "Second Buyer"
        ));
        workspace = workspaceRepository.save(workspace(creator));
        pack = packRepository.save(pack(creator, workspace));
    }

    @Test
    void isolatesReviewsAndAggregatesByExactVersion() {
        MarketplacePackVersion first = versionRepository.save(version(pack, 1));
        MarketplacePackVersion second = versionRepository.save(version(pack, 2));

        reviewRepository.save(review(first, buyer, 5));
        reviewRepository.save(review(first, secondBuyer, 3));
        reviewRepository.saveAndFlush(review(second, buyer, 2));

        assertThat(reviewRepository.findByPackVersionVersionIdAndUserUserId(
                first.getVersionId(),
                buyer.getUserId()
        )).get().extracting(MarketplaceReview::getRating).isEqualTo(5);
        assertThat(reviewRepository.findByPackVersionVersionIdAndUserUserId(
                second.getVersionId(),
                buyer.getUserId()
        )).get().extracting(MarketplaceReview::getRating).isEqualTo(2);
        assertThat(reviewRepository.findByPackVersionVersionIdOrderByUpdatedAtDesc(first.getVersionId()))
                .hasSize(2)
                .allMatch(review -> review.getPackVersion().getVersionId().equals(first.getVersionId()));

        List<MarketplaceReviewRepository.VersionRatingSummary> summaries =
                reviewRepository.summarizeByVersionIds(List.of(first.getVersionId(), second.getVersionId()));
        assertThat(summaries).hasSize(2);
        assertThat(summary(summaries, first.getVersionId()).getAverageRating()).isEqualTo(4D);
        assertThat(summary(summaries, first.getVersionId()).getReviewCount()).isEqualTo(2L);
        assertThat(summary(summaries, second.getVersionId()).getAverageRating()).isEqualTo(2D);
        assertThat(summary(summaries, second.getVersionId()).getReviewCount()).isEqualTo(1L);
    }

    @Test
    void legacyNullVersionQueriesDoNotLeakIntoVersionAggregates() {
        MarketplaceItem item = itemRepository.save(item());
        MarketplacePackVersion version = versionRepository.save(version(pack, 1));
        reviewRepository.save(review(version, buyer, 5));
        reviewRepository.saveAndFlush(legacyReview(item, buyer, 1));

        assertThat(reviewRepository.findByItemItemIdAndPackVersionIsNullOrderByUpdatedAtDesc(item.getItemId()))
                .singleElement()
                .extracting(MarketplaceReview::getRating)
                .isEqualTo(1);

        MarketplaceReviewRepository.VersionRatingSummary versionSummary = summary(
                reviewRepository.summarizeByVersionIds(List.of(version.getVersionId())),
                version.getVersionId()
        );
        assertThat(versionSummary.getAverageRating()).isEqualTo(5D);
        assertThat(versionSummary.getReviewCount()).isEqualTo(1L);

        MarketplaceReviewRepository.LegacyRatingSummary legacySummary = reviewRepository
                .summarizeLegacyByItemIds(List.of(item.getItemId()))
                .get(0);
        assertThat(legacySummary.getAverageRating()).isEqualTo(1D);
        assertThat(legacySummary.getReviewCount()).isEqualTo(1L);
    }

    private MarketplaceReviewRepository.VersionRatingSummary summary(
            List<MarketplaceReviewRepository.VersionRatingSummary> summaries,
            UUID versionId
    ) {
        return summaries.stream()
                .filter(summary -> versionId.equals(summary.getVersionId()))
                .findFirst()
                .orElseThrow();
    }

    private MarketplaceReview review(MarketplacePackVersion version, User user, int rating) {
        MarketplaceReview review = new MarketplaceReview();
        review.setPackVersion(version);
        review.setUser(user);
        review.setRating(rating);
        return review;
    }

    private MarketplaceReview legacyReview(MarketplaceItem item, User user, int rating) {
        MarketplaceReview review = new MarketplaceReview();
        review.setItem(item);
        review.setUser(user);
        review.setRating(rating);
        return review;
    }

    private MarketplacePack pack(User creator, StudyWorkspace workspace) {
        MarketplacePack pack = new MarketplacePack();
        pack.setCreator(creator);
        pack.setSourceWorkspace(workspace);
        return pack;
    }

    private MarketplacePackVersion version(MarketplacePack pack, int versionNo) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setPack(pack);
        version.setVersionNo(versionNo);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        version.setTitle("Review Pack " + versionNo);
        version.setSubject("Software");
        version.setPriceCoins(100);
        version.setChapterCount(1);
        version.setQuizCount(1);
        version.setQuestionCount(5);
        version.setContent(new ObjectMapper().createObjectNode());
        version.setSaleable(versionNo == 2);
        return version;
    }

    private MarketplaceItem item() {
        MarketplaceItem item = new MarketplaceItem();
        item.setCreator(creator);
        item.setSourceWorkspace(workspace);
        item.setTitle("Legacy Review Pack");
        item.setSubject("Software");
        item.setPriceCoins(100);
        item.setStatus(MarketplaceItemStatus.PUBLISHED);
        return item;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(user);
        workspace.setName("Review workspace");
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
