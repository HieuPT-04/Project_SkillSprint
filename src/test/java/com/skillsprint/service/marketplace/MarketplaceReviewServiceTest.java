package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewCollectionResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewContextResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceReviewIneligibilityReason;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.MarketplaceReviewMapper;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceReviewServiceTest {

    @Mock MarketplaceItemRepository itemRepository;
    @Mock MarketplaceReviewRepository reviewRepository;
    @Mock UserRepository userRepository;
    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplacePackVersionService packVersionService;
    @Mock MarketplaceOwnershipService marketplaceOwnershipService;
    @Mock MarketplaceVersionAccessService versionAccessService;
    @Mock MarketplaceLearningEligibilityService learningEligibilityService;

    MarketplaceReviewService service;
    UUID itemId;
    MarketplaceItem item;
    MarketplacePackVersion version;
    User buyer;

    @BeforeEach
    void setUp() {
        service = new MarketplaceReviewService(
                itemRepository,
                reviewRepository,
                userRepository,
                versionRepository,
                packVersionService,
                marketplaceOwnershipService,
                versionAccessService,
                learningEligibilityService,
                new MarketplaceReviewMapper()
        );
        itemId = UUID.randomUUID();
        item = new MarketplaceItem();
        item.setItemId(itemId);
        version = version(itemId);
        buyer = user("buyer", "Buyer");
    }

    @Test
    void eligibleOwnerCreatesReviewForExactVersion() {
        UpsertMarketplaceReviewRequest request = request(5, "Useful pack");
        when(versionAccessService.requireAndLockAccess("buyer", version.getVersionId())).thenReturn(version);
        when(reviewRepository.findByPackVersionVersionIdAndUserUserId(version.getVersionId(), "buyer"))
                .thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findById("buyer")).thenReturn(Optional.of(buyer));
        when(reviewRepository.saveAndFlush(any(MarketplaceReview.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        MarketplaceReviewResponse response = service.upsertVersion(
                "buyer",
                version.getVersionId(),
                request
        );

        assertThat(response.getReviewId()).isNotNull();
        assertThat(response.getVersionId()).isEqualTo(version.getVersionId());
        assertThat(response.getReviewerName()).isEqualTo("Buyer");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.isMine()).isTrue();
        verify(learningEligibilityService).requireCompletedQuiz("buyer", version.getVersionId());
    }

    @Test
    void repeatedUpsertUpdatesExistingVersionReview() {
        MarketplaceReview existing = review(version, buyer, 3, "Old");
        when(versionAccessService.requireAndLockAccess("buyer", version.getVersionId())).thenReturn(version);
        when(reviewRepository.findByPackVersionVersionIdAndUserUserId(version.getVersionId(), "buyer"))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.saveAndFlush(existing)).thenReturn(existing);

        MarketplaceReviewResponse response = service.upsertVersion(
                "buyer",
                version.getVersionId(),
                request(4, "Updated")
        );

        assertThat(response.getRating()).isEqualTo(4);
        assertThat(response.getComment()).isEqualTo("Updated");
        verifyNoInteractions(itemRepository, userRepository);
    }

    @Test
    void itemCompatibilityAdapterUsesMappedVersionInsteadOfLatestPackEntitlement() {
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.of(version));
        when(versionAccessService.requireAndLockAccess("buyer", version.getVersionId())).thenReturn(version);
        when(reviewRepository.findByPackVersionVersionIdAndUserUserId(version.getVersionId(), "buyer"))
                .thenReturn(Optional.of(review(version, buyer, 5, null)));
        when(reviewRepository.saveAndFlush(any(MarketplaceReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert("buyer", itemId, request(5, null));

        verify(versionAccessService).requireAndLockAccess("buyer", version.getVersionId());
        verifyNoInteractions(marketplaceOwnershipService);
    }

    @Test
    void contextExplainsMissingAccessWithoutCheckingQuizCompletion() {
        publishedVersion();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(versionAccessService.hasAccess("buyer", version)).thenReturn(false);
        when(reviewRepository.findByPackVersionVersionIdAndUserUserId(version.getVersionId(), "buyer"))
                .thenReturn(Optional.empty());

        MarketplaceReviewContextResponse response = service.getReviewContext("buyer", version.getVersionId());

        assertThat(response.isEligible()).isFalse();
        assertThat(response.getIneligibilityReason())
                .isEqualTo(MarketplaceReviewIneligibilityReason.ACCESS_REQUIRED);
        verify(learningEligibilityService, never()).hasCompletedQuiz(any(), any());
    }

    @Test
    void contextExplainsMissingVersionQuizCompletion() {
        publishedVersion();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(versionAccessService.hasAccess("buyer", version)).thenReturn(true);
        when(learningEligibilityService.hasCompletedQuiz("buyer", version.getVersionId())).thenReturn(false);
        when(reviewRepository.findByPackVersionVersionIdAndUserUserId(version.getVersionId(), "buyer"))
                .thenReturn(Optional.empty());

        MarketplaceReviewContextResponse response = service.getReviewContext("buyer", version.getVersionId());

        assertThat(response.isEligible()).isFalse();
        assertThat(response.getIneligibilityReason())
                .isEqualTo(MarketplaceReviewIneligibilityReason.QUIZ_COMPLETION_REQUIRED);
    }

    @Test
    void contextReturnsCurrentReviewWhenEligible() {
        publishedVersion();
        MarketplaceReview review = review(version, buyer, 5, "Great");
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(versionAccessService.hasAccess("buyer", version)).thenReturn(true);
        when(learningEligibilityService.hasCompletedQuiz("buyer", version.getVersionId())).thenReturn(true);
        when(reviewRepository.findByPackVersionVersionIdAndUserUserId(version.getVersionId(), "buyer"))
                .thenReturn(Optional.of(review));

        MarketplaceReviewContextResponse response = service.getReviewContext("buyer", version.getVersionId());

        assertThat(response.isEligible()).isTrue();
        assertThat(response.getIneligibilityReason()).isNull();
        assertThat(response.getCurrentUserReview().getReviewId()).isEqualTo(review.getReviewId());
    }

    @Test
    void versionCollectionReturnsOnlyRepositoryRowsForThatVersionAndAggregate() {
        publishedVersion();
        MarketplaceReview review = review(version, user("other", "Other"), 4, "Good");
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(reviewRepository.findByPackVersionVersionIdOrderByUpdatedAtDesc(version.getVersionId()))
                .thenReturn(List.of(review));
        when(reviewRepository.summarizeByVersionIds(List.of(version.getVersionId())))
                .thenReturn(List.of(summary(version.getVersionId(), 4D, 1L)));

        MarketplaceReviewCollectionResponse response = service.getVersionReviews(
                "buyer",
                version.getVersionId()
        );

        assertThat(response.getAverageRating()).isEqualTo(4D);
        assertThat(response.getReviewCount()).isEqualTo(1);
        assertThat(response.getReviews()).singleElement().satisfies(value -> {
            assertThat(value.getReviewerName()).isEqualTo("Other");
            assertThat(value.isMine()).isFalse();
        });
    }

    @Test
    void nonPublishedVersionIsReadableOnlyByItsOwner() {
        version.setStatus(MarketplacePackVersionStatus.SUSPENDED);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(versionAccessService.hasAccess("stranger", version)).thenReturn(false);

        assertThatThrownBy(() -> service.getVersionReviews("stranger", version.getVersionId()))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
    }

    private UpsertMarketplaceReviewRequest request(int rating, String comment) {
        UpsertMarketplaceReviewRequest request = new UpsertMarketplaceReviewRequest();
        request.setRating(rating);
        request.setComment(comment);
        return request;
    }

    private MarketplaceReview persisted(MarketplaceReview review) {
        review.setReviewId(UUID.randomUUID());
        review.setCreatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        review.setUpdatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        return review;
    }

    private MarketplaceReview review(
            MarketplacePackVersion reviewVersion,
            User user,
            int rating,
            String comment
    ) {
        MarketplaceReview review = new MarketplaceReview();
        review.setReviewId(UUID.randomUUID());
        review.setItem(item);
        review.setPackVersion(reviewVersion);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(Instant.parse("2026-07-18T00:00:00Z"));
        review.setUpdatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        return review;
    }

    private MarketplaceReviewRepository.VersionRatingSummary summary(
            UUID versionId,
            double average,
            long count
    ) {
        return new MarketplaceReviewRepository.VersionRatingSummary() {
            @Override public UUID getVersionId() { return versionId; }
            @Override public Double getAverageRating() { return average; }
            @Override public Long getReviewCount() { return count; }
        };
    }

    private MarketplacePackVersion version(UUID legacyItemId) {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion packVersion = new MarketplacePackVersion();
        packVersion.setVersionId(UUID.randomUUID());
        packVersion.setPack(pack);
        packVersion.setLegacyItemId(legacyItemId);
        packVersion.setVersionNo(1);
        packVersion.setStatus(MarketplacePackVersionStatus.DRAFT);
        return packVersion;
    }

    private User user(String userId, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName(fullName);
        return user;
    }

    private void publishedVersion() {
        version.setStatus(MarketplacePackVersionStatus.PUBLISHED);
    }
}
