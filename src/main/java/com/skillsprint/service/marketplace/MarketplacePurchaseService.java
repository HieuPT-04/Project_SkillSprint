package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.PurchaseMarketplacePackVersionRequest;
import com.skillsprint.dto.response.marketplace.MarketplacePurchaseResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionPurchaseResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

/**
 * Compatibility adapter for the legacy item checkout route.
 *
 * <p>Every new purchase must run through the version-aware checkout transaction so it records
 * the entitlement and 80/20 settlement. The stable key makes a client retry of the old
 * no-body route replay-safe until the frontend has migrated.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePurchaseService {

    static final String LEGACY_ITEM_CHECKOUT_KEY_PREFIX = "legacy-item-checkout:";

    MarketplacePackVersionService packVersionService;
    MarketplaceVersionCheckoutService versionCheckoutService;

    public MarketplacePurchaseResponse purchaseWithCoins(String userId, UUID itemId) {
        MarketplacePackVersion version = packVersionService.requireByItemId(itemId);
        PurchaseMarketplacePackVersionRequest request = new PurchaseMarketplacePackVersionRequest();
        request.setIdempotencyKey(legacyIdempotencyKey(userId, itemId));
        MarketplaceVersionPurchaseResponse receipt = versionCheckoutService.purchaseWithCoins(
                userId, version.getVersionId(), request);

        return MarketplacePurchaseResponse.builder()
                .purchaseId(receipt.getSaleId())
                .itemId(itemId)
                .packId(receipt.getPackId())
                .versionId(receipt.getPackVersionId())
                .versionNo(receipt.getVersionNo())
                .priceCoins(receipt.getGrossCoinAmount())
                .remainingCoins(receipt.getRemainingCoinBalance())
                .purchasedAt(receipt.getPurchasedAt())
                .build();
    }

    private String legacyIdempotencyKey(String userId, UUID itemId) {
        UUID stableRequestId = UUID.nameUUIDFromBytes(
                (userId + '\u0000' + itemId).getBytes(StandardCharsets.UTF_8));
        return LEGACY_ITEM_CHECKOUT_KEY_PREFIX + stableRequestId;
    }
}
