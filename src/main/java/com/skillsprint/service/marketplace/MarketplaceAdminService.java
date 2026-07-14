package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.ReviewMarketplaceItemRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceAdminItemDetailResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceAdminService {

    MarketplaceItemRepository marketplaceItemRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> getPendingItems() {
        return marketplaceItemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PENDING_REVIEW)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceAdminItemDetailResponse getItemDetail(UUID itemId) {
        MarketplaceItem item = findItem(itemId);
        MarketplaceQuizPackSnapshot snapshot = findSnapshot(itemId);
        return MarketplaceAdminItemDetailResponse.builder()
                .itemId(item.getItemId())
                .creatorId(item.getCreator().getUserId())
                .creatorName(item.getCreator().getFullName())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .priceCoins(item.getPriceCoins())
                .status(item.getStatus().name())
                .creatorValidationScore(item.getCreatorValidationScore())
                .reviewNote(item.getReviewNote())
                .createdAt(item.getCreatedAt())
                .content(snapshot.getContent())
                .build();
    }

    @Transactional
    public MarketplaceItemResponse review(String adminUserId, UUID itemId, ReviewMarketplaceItemRequest request) {
        MarketplaceItem item = findItem(itemId);
        validateTransition(item.getStatus(), request.getStatus());
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        item.setStatus(request.getStatus());
        item.setReviewedBy(admin);
        item.setReviewNote(request.getReviewNote());
        item.setReviewedAt(Instant.now());
        item.setPublishedAt(request.getStatus() == MarketplaceItemStatus.PUBLISHED ? Instant.now() : null);
        item = marketplaceItemRepository.save(item);
        return toResponse(item);
    }

    private void validateTransition(MarketplaceItemStatus current, MarketplaceItemStatus target) {
        boolean valid = (current == MarketplaceItemStatus.PENDING_REVIEW
                && (target == MarketplaceItemStatus.PUBLISHED || target == MarketplaceItemStatus.REJECTED))
                || (current == MarketplaceItemStatus.PUBLISHED && target == MarketplaceItemStatus.SUSPENDED);
        if (!valid) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Trạng thái kiểm duyệt không hợp lệ");
        }
    }

    private MarketplaceItem findItem(UUID itemId) {
        return marketplaceItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private MarketplaceQuizPackSnapshot findSnapshot(UUID itemId) {
        return snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private MarketplaceItemResponse toResponse(MarketplaceItem item) {
        MarketplaceQuizPackSnapshot snapshot = findSnapshot(item.getItemId());
        return MarketplaceItemResponse.builder()
                .itemId(item.getItemId())
                .sourceWorkspaceId(item.getSourceWorkspace().getWorkspaceId())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .priceCoins(item.getPriceCoins())
                .status(item.getStatus())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .creatorValidationScore(item.getCreatorValidationScore())
                .reviewNote(item.getReviewNote())
                .createdAt(item.getCreatedAt())
                .publishedAt(item.getPublishedAt())
                .build();
    }
}
