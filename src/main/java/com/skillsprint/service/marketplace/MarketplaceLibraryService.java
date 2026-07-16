package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.*;
import com.skillsprint.entity.*;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.exception.*;
import com.skillsprint.repository.*;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceLibraryService {
    MarketplacePurchaseRepository purchaseRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    MarketplacePackVersionService packVersionService;

    @Transactional(readOnly = true)
    public List<MarketplaceCatalogItemResponse> getMyPacks(String userId) {
        List<MarketplacePurchase> purchases = purchaseRepository
                .findByUserUserIdAndStatusOrderByPurchasedAtDesc(userId, MarketplacePurchaseStatus.ACTIVE);
        Map<UUID, MarketplacePackVersionIdentity> identities = packVersionService
                .identitiesOf(purchases.stream().map(p -> p.getItem().getItemId()).distinct().toList());
        return purchases.stream().map(p -> {
            UUID itemId = p.getItem().getItemId();
            MarketplacePackVersionIdentity identity =
                    identities.getOrDefault(itemId, MarketplacePackVersionIdentity.EMPTY);
            MarketplaceQuizPackSnapshot snapshot = snapshot(itemId);
            return MarketplaceCatalogItemResponse.builder().itemId(itemId)
                    .packId(identity.packId()).versionId(identity.versionId()).versionNo(identity.versionNo())
                    .title(p.getItem().getTitle())
                    .description(p.getItem().getDescription()).subject(p.getItem().getSubject())
                    .creatorName(p.getItem().getCreator().getFullName())
                    .priceCoins(p.getPriceCoins()).chapterCount(snapshot.getChapterCount())
                    .quizCount(snapshot.getQuizCount())
                    .questionCount(snapshot.getQuestionCount())
                    .publishedAt(p.getItem().getPublishedAt()).build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public PurchasedQuizPackResponse getMyPack(String userId, UUID itemId) {
        if (!purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(userId, itemId,
                MarketplacePurchaseStatus.ACTIVE))
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn chưa mua Quiz Pack này");
        MarketplaceQuizPackSnapshot snapshot = snapshot(itemId);
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        JsonNode safe = snapshot.getContent().deepCopy();
        scrubCorrectAnswers(safe);
        return PurchasedQuizPackResponse.builder().itemId(itemId)
                .packId(identity.packId()).versionId(identity.versionId()).versionNo(identity.versionNo())
                .title(snapshot.getItem().getTitle())
                .subject(snapshot.getItem().getSubject())
                .questionCount(snapshot.getQuestionCount()).content(safe).build();
    }

    private MarketplaceQuizPackSnapshot snapshot(UUID itemId) {
        return snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private void scrubCorrectAnswers(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("correct");
            object.fields().forEachRemaining(entry -> scrubCorrectAnswers(entry.getValue()));
        } else if (node.isArray())
            node.forEach(this::scrubCorrectAnswers);
    }
}
