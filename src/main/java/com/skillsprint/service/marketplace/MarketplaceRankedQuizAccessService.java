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

/**
 * Resolves access to the version-first Ranked Quiz while Version 1 legacy purchases
 * coexist with version entitlements.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedQuizAccessService {

    MarketplacePackVersionRepository versionRepository;
    MarketplaceEntitlementRepository entitlementRepository;
    MarketplacePurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public MarketplacePackVersion requireRankedAccess(String buyerId, UUID versionId) {
        MarketplacePackVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));

        if (entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyerId, versionId, MarketplaceEntitlementStatus.ACTIVE)) {
            return version;
        }

        UUID legacyItemId = version.getLegacyItemId();
        if (Integer.valueOf(1).equals(version.getVersionNo())
                && legacyItemId != null
                && purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                buyerId, legacyItemId, MarketplacePurchaseStatus.ACTIVE)) {
            return version;
        }

        throw new AppException(ErrorCode.FORBIDDEN, "Bạn chưa sở hữu phiên bản Quiz Pack này");
    }

    /**
     * Locks the access record that authorizes this buyer. Start/resume uses this
     * lock to prevent concurrent requests from creating separate active attempts.
     */
    @Transactional
    public MarketplacePackVersion requireAndLockRankedAccess(String buyerId, UUID versionId) {
        MarketplacePackVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));

        if (entitlementRepository.findByBuyerAndVersionAndStatusForUpdate(
                buyerId, versionId, MarketplaceEntitlementStatus.ACTIVE).isPresent()) {
            return version;
        }

        UUID legacyItemId = version.getLegacyItemId();
        if (Integer.valueOf(1).equals(version.getVersionNo())
                && legacyItemId != null
                && purchaseRepository.findByBuyerAndItemAndStatusForUpdate(
                buyerId, legacyItemId, MarketplacePurchaseStatus.ACTIVE).isPresent()) {
            return version;
        }

        throw new AppException(ErrorCode.FORBIDDEN, "Bạn chưa sở hữu phiên bản Quiz Pack này");
    }
}
