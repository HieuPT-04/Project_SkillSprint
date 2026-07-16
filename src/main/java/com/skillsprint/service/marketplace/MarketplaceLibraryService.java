package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplaceCatalogItemResponse;
import com.skillsprint.dto.response.marketplace.PurchasedQuizPackResponse;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePurchase;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceLibraryService {

    MarketplacePurchaseRepository purchaseRepository;
    MarketplaceEntitlementRepository entitlementRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    MarketplacePackVersionService packVersionService;
    MarketplaceOwnershipService marketplaceOwnershipService;

    @Transactional(readOnly = true)
    public List<MarketplaceCatalogItemResponse> getMyPacks(String userId) {
        List<MarketplacePurchase> purchases = purchaseRepository
                .findByUserUserIdAndStatusOrderByPurchasedAtDesc(userId, MarketplacePurchaseStatus.ACTIVE);
        Map<UUID, MarketplacePackVersionIdentity> identities = packVersionService.identitiesOf(
                purchases.stream().map(purchase -> purchase.getItem().getItemId()).distinct().toList());
        List<MarketplaceCatalogItemResponse> legacyPacks = purchases.stream()
                .map(purchase -> legacyCatalogResponse(
                        purchase,
                        identities.getOrDefault(purchase.getItem().getItemId(), MarketplacePackVersionIdentity.EMPTY)))
                .toList();

        Set<UUID> legacyVersionIds = purchases.stream()
                .map(MarketplacePurchase::getPackVersion)
                .filter(Objects::nonNull)
                .map(MarketplacePackVersion::getVersionId)
                .collect(Collectors.toSet());
        List<MarketplaceCatalogItemResponse> entitlementPacks = entitlementRepository
                .findByBuyerUserIdAndStatusOrderByGrantedAtDesc(userId, MarketplaceEntitlementStatus.ACTIVE)
                .stream()
                .map(MarketplaceEntitlement::getPackVersion)
                .filter(version -> !legacyVersionIds.contains(version.getVersionId()))
                .map(this::catalogResponse)
                .toList();

        return Stream.concat(legacyPacks.stream(), entitlementPacks.stream()).toList();
    }

    @Transactional(readOnly = true)
    public PurchasedQuizPackResponse getMyPack(String userId, UUID itemId) {
        MarketplaceOwnershipService.Ownership ownership = marketplaceOwnershipService.requireActiveOwnership(
                userId, itemId, "Bạn chưa mua Quiz Pack này");
        if (ownership.isLegacyPurchase()) {
            return legacyPackResponse(itemId);
        }

        MarketplacePackVersion version = ownership.packVersion();
        JsonNode safe = version.getContent().deepCopy();
        scrubCorrectAnswers(safe);
        return PurchasedQuizPackResponse.builder()
                .itemId(version.getLegacyItemId())
                .packId(version.getPack().getPackId())
                .versionId(version.getVersionId())
                .versionNo(version.getVersionNo())
                .title(version.getTitle())
                .subject(version.getSubject())
                .questionCount(version.getQuestionCount())
                .content(safe)
                .build();
    }

    private MarketplaceCatalogItemResponse legacyCatalogResponse(
            MarketplacePurchase purchase,
            MarketplacePackVersionIdentity identity
    ) {
        UUID itemId = purchase.getItem().getItemId();
        MarketplaceQuizPackSnapshot snapshot = snapshot(itemId);
        return MarketplaceCatalogItemResponse.builder()
                .itemId(itemId)
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .title(purchase.getItem().getTitle())
                .description(purchase.getItem().getDescription())
                .subject(purchase.getItem().getSubject())
                .creatorName(purchase.getItem().getCreator().getFullName())
                .priceCoins(purchase.getPriceCoins())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .publishedAt(purchase.getItem().getPublishedAt())
                .build();
    }

    private MarketplaceCatalogItemResponse catalogResponse(MarketplacePackVersion version) {
        return MarketplaceCatalogItemResponse.builder()
                .itemId(version.getLegacyItemId())
                .packId(version.getPack().getPackId())
                .versionId(version.getVersionId())
                .versionNo(version.getVersionNo())
                .title(version.getTitle())
                .description(version.getDescription())
                .subject(version.getSubject())
                .creatorName(version.getPack().getCreator().getFullName())
                .priceCoins(version.getPriceCoins())
                .chapterCount(version.getChapterCount())
                .quizCount(version.getQuizCount())
                .questionCount(version.getQuestionCount())
                .publishedAt(version.getPublishedAt())
                .build();
    }

    private PurchasedQuizPackResponse legacyPackResponse(UUID itemId) {
        MarketplaceQuizPackSnapshot snapshot = snapshot(itemId);
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        JsonNode safe = snapshot.getContent().deepCopy();
        scrubCorrectAnswers(safe);
        return PurchasedQuizPackResponse.builder()
                .itemId(itemId)
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .title(snapshot.getItem().getTitle())
                .subject(snapshot.getItem().getSubject())
                .questionCount(snapshot.getQuestionCount())
                .content(safe)
                .build();
    }

    private MarketplaceQuizPackSnapshot snapshot(UUID itemId) {
        return snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private void scrubCorrectAnswers(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("correct");
            object.fields().forEachRemaining(entry -> scrubCorrectAnswers(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::scrubCorrectAnswers);
        }
    }
}
