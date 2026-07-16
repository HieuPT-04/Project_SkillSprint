package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.*;
import com.skillsprint.enums.marketplace.*;
import com.skillsprint.exception.*;
import com.skillsprint.repository.*;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceReviewService {
    MarketplaceItemRepository itemRepository; MarketplaceReviewRepository reviewRepository; UserRepository userRepository;
    MarketplacePackVersionService packVersionService;
    MarketplaceOwnershipService marketplaceOwnershipService;

    @Transactional public MarketplaceReviewResponse upsert(String userId, UUID itemId, UpsertMarketplaceReviewRequest request) {
        marketplaceOwnershipService.requireActiveOwnership(userId, itemId, "Bạn cần mua Quiz Pack trước khi đánh giá");
        MarketplaceReview review=reviewRepository.findByItemItemIdAndUserUserId(itemId,userId).orElseGet(MarketplaceReview::new);
        if(review.getReviewId()==null) { review.setItem(itemRepository.findById(itemId).orElseThrow(()->new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND))); review.setUser(userRepository.findById(userId).orElseThrow(()->new AppException(ErrorCode.USER_PROFILE_NOT_FOUND))); }
        if(review.getPackVersion()==null) review.setPackVersion(packVersionService.findByItemId(itemId).orElse(null));
        review.setRating(request.getRating()); review.setComment(request.getComment());
        return response(reviewRepository.save(review), packVersionService.identityOf(itemId));
    }

    // Every review in the list belongs to the same item, so the identity resolves once.
    @Transactional(readOnly=true) public List<MarketplaceReviewResponse> getReviews(UUID itemId) {
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        return reviewRepository.findAll().stream().filter(r->r.getItem().getItemId().equals(itemId)).map(r->response(r, identity)).toList();
    }

    private MarketplaceReviewResponse response(MarketplaceReview r, MarketplacePackVersionIdentity identity) {
        return MarketplaceReviewResponse.builder()
                .packId(identity.packId()).versionId(identity.versionId()).versionNo(identity.versionNo())
                .userName(r.getUser().getFullName()).rating(r.getRating()).comment(r.getComment()).updatedAt(r.getUpdatedAt()).build();
    }
}
