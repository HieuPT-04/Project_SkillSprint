package com.skillsprint.service.marketplace;

import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves learner access while the legacy item purchase and version entitlement
 * checkout paths coexist. This is the single policy boundary for Marketplace ownership.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceOwnershipService {

    MarketplacePurchaseRepository purchaseRepository;
    MarketplaceEntitlementRepository entitlementRepository;
    MarketplacePackVersionService packVersionService;

    @Transactional(readOnly = true)
    public Optional<Ownership> findActiveOwnership(String userId, UUID itemId) {
        if (purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                userId, itemId, MarketplacePurchaseStatus.ACTIVE)) {
            // Do not require a version here: a historical owner must keep access to
            // the immutable legacy snapshot even if migration data is incomplete.
            return Optional.of(Ownership.legacyPurchase());
        }

        return packVersionService.findByItemId(itemId)
                .filter(version -> entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                        userId, version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE))
                .map(Ownership::entitlement);
    }

    @Transactional(readOnly = true)
    public Ownership requireActiveOwnership(String userId, UUID itemId, String deniedMessage) {
        return findActiveOwnership(userId, itemId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, deniedMessage));
    }

    public enum Source {
        LEGACY_PURCHASE,
        ENTITLEMENT
    }

    public record Ownership(Source source, MarketplacePackVersion packVersion) {

        static Ownership legacyPurchase() {
            return new Ownership(Source.LEGACY_PURCHASE, null);
        }

        static Ownership entitlement(MarketplacePackVersion packVersion) {
            return new Ownership(Source.ENTITLEMENT, packVersion);
        }

        public boolean isLegacyPurchase() {
            return source == Source.LEGACY_PURCHASE;
        }
    }
}
