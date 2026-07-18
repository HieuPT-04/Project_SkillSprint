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
        Optional<MarketplacePackVersion> mappedVersion = packVersionService.findByItemId(itemId);
        if (mappedVersion.isPresent()) {
            Optional<MarketplacePackVersion> latestEntitledVersion = entitlementRepository
                    .findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdOrderByPackVersionVersionNoDesc(
                            userId,
                            MarketplaceEntitlementStatus.ACTIVE,
                            mappedVersion.get().getPack().getPackId()
                    )
                    .map(entitlement -> entitlement.getPackVersion());
            if (latestEntitledVersion.isPresent()) {
                return latestEntitledVersion.map(Ownership::entitlement);
            }
        }

        if (purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                userId, itemId, MarketplacePurchaseStatus.ACTIVE)) {
            // Historical owners retain access even if the V1 mapping is incomplete.
            return Optional.of(Ownership.legacyPurchase(mappedVersion.orElse(null)));
        }

        return Optional.empty();
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

        static Ownership legacyPurchase(MarketplacePackVersion packVersion) {
            return new Ownership(Source.LEGACY_PURCHASE, packVersion);
        }

        static Ownership entitlement(MarketplacePackVersion packVersion) {
            return new Ownership(Source.ENTITLEMENT, packVersion);
        }

        public boolean isLegacyPurchase() {
            return source == Source.LEGACY_PURCHASE;
        }
    }
}
