package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.response.marketplace.MarketplaceCatalogItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemDetailResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceCatalogServiceTest {

    @Mock MarketplaceItemRepository marketplaceItemRepository;
    @Mock MarketplaceQuizPackSnapshotRepository snapshotRepository;
    @Mock MarketplaceReviewRepository reviewRepository;
    @Mock MarketplacePackVersionService packVersionService;
    @Mock S3PresignedUrlService s3PresignedUrlService;

    @Test
    void returnsCreatorAvatarAsPresignedViewUrlWithoutExposingObjectKey() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = publishedItem(itemId);
        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setChapterCount(1);
        snapshot.setQuizCount(1);
        snapshot.setQuestionCount(5);
        snapshot.setContent(new ObjectMapper().createObjectNode().putArray("chapters"));
        when(marketplaceItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));
        when(packVersionService.identityOf(itemId)).thenReturn(MarketplacePackVersionIdentity.EMPTY);
        when(reviewRepository.findByItemItemId(itemId)).thenReturn(List.of());
        when(s3PresignedUrlService.createViewUrl("users/creator/avatar/avatar.webp"))
                .thenReturn("https://signed.example/avatar.webp");

        MarketplaceCatalogService service = new MarketplaceCatalogService(
                marketplaceItemRepository,
                snapshotRepository,
                reviewRepository,
                packVersionService,
                s3PresignedUrlService
        );

        MarketplaceItemDetailResponse response = service.getPublishedItem(itemId);

        assertThat(response.getCreatorAvatarUrl()).isEqualTo("https://signed.example/avatar.webp");
        verify(s3PresignedUrlService).createViewUrl("users/creator/avatar/avatar.webp");
    }

    @Test
    void returnsCreatorAvatarAsPresignedViewUrlInCatalog() {
        UUID itemId = UUID.randomUUID();
        MarketplaceItem item = publishedItem(itemId);
        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setChapterCount(1);
        snapshot.setQuizCount(1);
        snapshot.setQuestionCount(5);
        when(marketplaceItemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PUBLISHED))
                .thenReturn(List.of(item));
        when(snapshotRepository.findByItemItemId(itemId)).thenReturn(Optional.of(snapshot));
        when(packVersionService.identitiesOf(List.of(itemId)))
                .thenReturn(Map.of(itemId, MarketplacePackVersionIdentity.EMPTY));
        when(reviewRepository.findByItemItemId(itemId)).thenReturn(List.of());
        when(s3PresignedUrlService.createViewUrl("users/creator/avatar/avatar.webp"))
                .thenReturn("https://signed.example/avatar.webp");

        MarketplaceCatalogService service = new MarketplaceCatalogService(
                marketplaceItemRepository,
                snapshotRepository,
                reviewRepository,
                packVersionService,
                s3PresignedUrlService
        );

        MarketplaceCatalogItemResponse response = service.getPublishedItems(null).get(0);

        assertThat(response.getCreatorAvatarUrl()).isEqualTo("https://signed.example/avatar.webp");
        verify(s3PresignedUrlService).createViewUrl("users/creator/avatar/avatar.webp");
    }

    private MarketplaceItem publishedItem(UUID itemId) {
        User creator = new User();
        creator.setUserId("creator");
        creator.setFullName("Creator");
        creator.setAvatarObjectKey("users/creator/avatar/avatar.webp");
        MarketplaceItem item = new MarketplaceItem();
        item.setItemId(itemId);
        item.setCreator(creator);
        item.setTitle("Marketplace pack");
        item.setSubject("Software");
        item.setPriceCoins(100);
        item.setStatus(MarketplaceItemStatus.PUBLISHED);
        return item;
    }
}
