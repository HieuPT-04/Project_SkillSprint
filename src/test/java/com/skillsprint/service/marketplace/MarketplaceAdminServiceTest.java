package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.marketplace.ReviewMarketplaceItemRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceQualityJobResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceAdminServiceTest {

    @Mock MarketplaceItemRepository marketplaceItemRepository;
    @Mock MarketplaceQuizPackSnapshotRepository snapshotRepository;
    @Mock MarketplacePackVersionService packVersionService;
    @Mock MarketplaceQualityService qualityService;
    @Mock UserRepository userRepository;
    @InjectMocks MarketplaceAdminService service;

    @BeforeEach
    void stubVersionIdentity() {
        // These tests predate the pack/version foundation and assert legacy item
        // behavior only; the additive identity fields have their own coverage in
        // MarketplacePackVersionCompatibilityTest.
        lenient().when(packVersionService.identityOf(any()))
                .thenReturn(MarketplacePackVersionIdentity.EMPTY);
        lenient().when(qualityService.summariesByLegacyItemIds(any()))
                .thenReturn(java.util.Map.of());
    }

    @Test
    void listDefaultsToPendingReview() {
        MarketplaceItem item = item(MarketplaceItemStatus.PENDING_REVIEW);
        when(marketplaceItemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PENDING_REVIEW))
                .thenReturn(List.of(item));
        when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot(item)));

        List<MarketplaceItemResponse> result = service.getItems(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(MarketplaceItemStatus.PENDING_REVIEW);
        verify(marketplaceItemRepository).findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PENDING_REVIEW);
    }

    @Test
    void listAcceptsPublishedStatus() {
        MarketplaceItem item = item(MarketplaceItemStatus.PUBLISHED);
        when(marketplaceItemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PUBLISHED))
                .thenReturn(List.of(item));
        when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot(item)));

        List<MarketplaceItemResponse> result = service.getItems(MarketplaceItemStatus.PUBLISHED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(MarketplaceItemStatus.PUBLISHED);
        verify(marketplaceItemRepository).findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PUBLISHED);
    }

    @Test
    void listIncludesCurrentQualitySummaryWithoutLoadingJobReports() {
        MarketplaceItem item = item(MarketplaceItemStatus.PENDING_REVIEW);
        when(marketplaceItemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PENDING_REVIEW))
                .thenReturn(List.of(item));
        when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot(item)));
        when(qualityService.summariesByLegacyItemIds(List.of(item.getItemId())))
                .thenReturn(java.util.Map.of(item.getItemId(),
                        new MarketplaceQualityService.Summary(MarketplaceQualityJobStatus.PASSED, 95, true)));

        MarketplaceItemResponse response = service.getItems(null).get(0);

        assertThat(response.getQualityStatus()).isEqualTo(MarketplaceQualityJobStatus.PASSED);
        assertThat(response.getQualityScore()).isEqualTo(95);
        assertThat(response.isQualityCurrent()).isTrue();
    }

    @Test
    void detailIncludesLatestVersionScopedQualityReport() {
        MarketplaceItem item = item(MarketplaceItemStatus.PENDING_REVIEW);
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        MarketplaceQualityJobResponse qualityJob = MarketplaceQualityJobResponse.builder()
                .jobId(UUID.randomUUID())
                .versionId(version.getVersionId())
                .status(MarketplaceQualityJobStatus.PASSED)
                .score(95)
                .currentSnapshot(true)
                .build();
        when(marketplaceItemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot(item)));
        when(packVersionService.findByItemId(item.getItemId())).thenReturn(Optional.of(version));
        when(qualityService.findLatestForAdmin(version)).thenReturn(Optional.of(qualityJob));
        when(qualityService.findRecentForAdmin(version)).thenReturn(List.of(qualityJob));

        var response = service.getItemDetail(item.getItemId());

        assertThat(response.getQualityJob()).isSameAs(qualityJob);
        assertThat(response.getQualityJobHistory()).containsExactly(qualityJob);
    }

    @Test
    void rejectionReturnsItemToDraftAndKeepsReviewerAndNote() {
        MarketplaceItem item = item(MarketplaceItemStatus.PENDING_REVIEW);
        item.setCreatorValidationScore(95);
        User admin = new User();
        admin.setUserId("admin");
        when(marketplaceItemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(marketplaceItemRepository.save(any(MarketplaceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot(item)));

        ReviewMarketplaceItemRequest request = new ReviewMarketplaceItemRequest();
        request.setStatus(MarketplaceItemStatus.REJECTED);
        request.setReviewNote("Thieu noi dung chuong 2");

        MarketplaceItemResponse response = service.review("admin", item.getItemId(), request);

        assertThat(response.getStatus()).isEqualTo(MarketplaceItemStatus.DRAFT);
        assertThat(item.getStatus()).isEqualTo(MarketplaceItemStatus.DRAFT);
        assertThat(item.getReviewedBy()).isSameAs(admin);
        assertThat(item.getReviewedAt()).isNotNull();
        assertThat(item.getCreatorValidationScore()).isNull();
        assertThat(response.getReviewNote()).isEqualTo("Thieu noi dung chuong 2");
        verify(qualityService, never()).requireCurrentPass(any());
    }

    @Test
    void publishingPendingItemSetsPublishedAt() {
        MarketplaceItem item = item(MarketplaceItemStatus.PENDING_REVIEW);
        MarketplacePackVersion version = new MarketplacePackVersion();
        User admin = new User();
        admin.setUserId("admin");
        when(marketplaceItemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(packVersionService.requireByItemId(item.getItemId())).thenReturn(version);
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(marketplaceItemRepository.save(any(MarketplaceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot(item)));

        ReviewMarketplaceItemRequest request = new ReviewMarketplaceItemRequest();
        request.setStatus(MarketplaceItemStatus.PUBLISHED);

        MarketplaceItemResponse response = service.review("admin", item.getItemId(), request);

        assertThat(response.getStatus()).isEqualTo(MarketplaceItemStatus.PUBLISHED);
        assertThat(item.getPublishedAt()).isNotNull();
        verify(qualityService).requireCurrentPass(version);
    }

    @Test
    void publishingWithoutCurrentQualityPassLeavesItemUntouched() {
        MarketplaceItem item = item(MarketplaceItemStatus.PENDING_REVIEW);
        MarketplacePackVersion version = new MarketplacePackVersion();
        when(marketplaceItemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(packVersionService.requireByItemId(item.getItemId())).thenReturn(version);
        doThrow(new AppException(ErrorCode.MARKETPLACE_QUALITY_VALIDATION_REQUIRED))
                .when(qualityService).requireCurrentPass(version);

        ReviewMarketplaceItemRequest request = new ReviewMarketplaceItemRequest();
        request.setStatus(MarketplaceItemStatus.PUBLISHED);

        assertThatThrownBy(() -> service.review("admin", item.getItemId(), request))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_QUALITY_VALIDATION_REQUIRED);
        assertThat(item.getStatus()).isEqualTo(MarketplaceItemStatus.PENDING_REVIEW);
        assertThat(item.getPublishedAt()).isNull();
        verify(userRepository, never()).findById(any());
        verify(marketplaceItemRepository, never()).save(any());
        verify(packVersionService, never()).syncFromLegacyItem(any(), any());
    }

    @Test
    void invalidStatusTransitionsAreRejected() {
        assertInvalidTransition(MarketplaceItemStatus.DRAFT, MarketplaceItemStatus.PUBLISHED);
        assertInvalidTransition(MarketplaceItemStatus.DRAFT, MarketplaceItemStatus.REJECTED);
        assertInvalidTransition(MarketplaceItemStatus.PENDING_REVIEW, MarketplaceItemStatus.SUSPENDED);
        assertInvalidTransition(MarketplaceItemStatus.PENDING_REVIEW, MarketplaceItemStatus.DRAFT);
        assertInvalidTransition(MarketplaceItemStatus.PUBLISHED, MarketplaceItemStatus.REJECTED);
        assertInvalidTransition(MarketplaceItemStatus.SUSPENDED, MarketplaceItemStatus.PUBLISHED);
        verify(marketplaceItemRepository, never()).save(any(MarketplaceItem.class));
    }

    private void assertInvalidTransition(MarketplaceItemStatus current, MarketplaceItemStatus requested) {
        MarketplaceItem item = item(current);
        when(marketplaceItemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        ReviewMarketplaceItemRequest request = new ReviewMarketplaceItemRequest();
        request.setStatus(requested);

        assertThatThrownBy(() -> service.review("admin", item.getItemId(), request))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(item.getStatus()).isEqualTo(current);
    }

    private MarketplaceItem item(MarketplaceItemStatus status) {
        User creator = new User();
        creator.setUserId("creator");
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(creator);
        MarketplaceItem item = new MarketplaceItem();
        item.setItemId(UUID.randomUUID());
        item.setCreator(creator);
        item.setSourceWorkspace(workspace);
        item.setTitle("Pack");
        item.setSubject("Toan");
        item.setPriceCoins(100);
        item.setStatus(status);
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
}
