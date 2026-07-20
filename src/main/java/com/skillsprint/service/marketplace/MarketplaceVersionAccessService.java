package com.skillsprint.service.marketplace;

import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes buyer access to an immutable pack version. */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceVersionAccessService {

    MarketplacePackVersionRepository versionRepository;
    MarketplaceEntitlementRepository entitlementRepository;
    MarketplacePurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public MarketplacePackVersion requireAccess(String buyerId, UUID versionId) {
        MarketplacePackVersion version = requireVersion(versionId);

        if (hasAccess(buyerId, version)) {
            return version;
        }

        throw accessDenied();
    }

    /** Checks access for a version already resolved by the calling transaction. */
    public boolean hasAccess(String buyerId, MarketplacePackVersion version) {
        return entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyerId, version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)
                || hasActiveLegacyPurchase(buyerId, version);
    }

    /** Locks the access record before a start-or-resume write operation. */
    @Transactional
    public MarketplacePackVersion requireAndLockAccess(String buyerId, UUID versionId) {
        MarketplacePackVersion version = requireVersion(versionId);

        if (entitlementRepository.findByBuyerAndVersionAndStatusForUpdate(
                buyerId, versionId, MarketplaceEntitlementStatus.ACTIVE).isPresent()
                || hasLockedActiveLegacyPurchase(buyerId, version)) {
            return version;
        }

        throw accessDenied();
    }

    private MarketplacePackVersion requireVersion(UUID versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
    }

    private boolean hasActiveLegacyPurchase(String buyerId, MarketplacePackVersion version) {
        return isLegacyVersionOne(version)
                && purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                buyerId, version.getLegacyItemId(), MarketplacePurchaseStatus.ACTIVE);
    }

    private boolean hasLockedActiveLegacyPurchase(String buyerId, MarketplacePackVersion version) {
        return isLegacyVersionOne(version)
                && purchaseRepository.findByBuyerAndItemAndStatusForUpdate(
                buyerId, version.getLegacyItemId(), MarketplacePurchaseStatus.ACTIVE).isPresent();
    }

    private boolean isLegacyVersionOne(MarketplacePackVersion version) {
        return Integer.valueOf(1).equals(version.getVersionNo()) && version.getLegacyItemId() != null;
    }

    private AppException accessDenied() {
        return new AppException(ErrorCode.FORBIDDEN, "Bạn chưa sở hữu phiên bản Quiz Pack này");
    }
}
