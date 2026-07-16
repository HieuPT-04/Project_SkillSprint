package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceReview;
import com.skillsprint.entity.User;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.UserRepository;
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
    @Mock MarketplacePackVersionService packVersionService;
    @Mock MarketplaceOwnershipService marketplaceOwnershipService;

    private MarketplaceReviewService service;
    private UUID itemId;
    private MarketplacePackVersion version;

    @BeforeEach
    void setUp() {
        service = new MarketplaceReviewService(
                itemRepository, reviewRepository, userRepository, packVersionService, marketplaceOwnershipService);
        itemId = UUID.randomUUID();
        version = version(itemId);
    }

    @Test
    void entitlementOwnerCanCreateReview() {
        User buyer = new User();
        buyer.setUserId("buyer");
        buyer.setFullName("Buyer");
        MarketplaceItem item = new MarketplaceItem();
        item.setItemId(itemId);
        UpsertMarketplaceReviewRequest request = new UpsertMarketplaceReviewRequest();
        request.setRating(5);
        request.setComment("Useful pack");
        MarketplaceReview saved = new MarketplaceReview();
        saved.setItem(item);
        saved.setUser(buyer);
        saved.setRating(5);
        saved.setComment("Useful pack");

        when(marketplaceOwnershipService.requireActiveOwnership(
                "buyer", itemId, "Bạn cần mua Quiz Pack trước khi đánh giá"))
                .thenReturn(new MarketplaceOwnershipService.Ownership(
                        MarketplaceOwnershipService.Source.ENTITLEMENT, version));
        when(reviewRepository.findByItemItemIdAndUserUserId(itemId, "buyer")).thenReturn(Optional.empty());
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findById("buyer")).thenReturn(Optional.of(buyer));
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.of(version));
        when(packVersionService.identityOf(itemId)).thenReturn(MarketplacePackVersionIdentity.of(version));
        when(reviewRepository.save(org.mockito.ArgumentMatchers.any(MarketplaceReview.class))).thenReturn(saved);

        MarketplaceReviewResponse response = service.upsert("buyer", itemId, request);

        assertThat(response.getRating()).isEqualTo(5);
        verify(marketplaceOwnershipService).requireActiveOwnership("buyer", itemId,
                "Bạn cần mua Quiz Pack trước khi đánh giá");
    }

    @Test
    void userWithoutOwnershipCannotCreateReview() {
        when(marketplaceOwnershipService.requireActiveOwnership(anyString(), org.mockito.ArgumentMatchers.any(), anyString()))
                .thenThrow(new AppException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.upsert("buyer", itemId, new UpsertMarketplaceReviewRequest()))
                .isInstanceOf(AppException.class);

        verifyNoInteractions(itemRepository, reviewRepository, userRepository, packVersionService);
    }

    private MarketplacePackVersion version(UUID itemId) {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setPack(pack);
        version.setLegacyItemId(itemId);
        version.setVersionNo(1);
        return version;
    }
}
