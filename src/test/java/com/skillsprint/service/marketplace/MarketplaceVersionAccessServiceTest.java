package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceVersionAccessServiceTest {

    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplaceEntitlementRepository entitlementRepository;
    @Mock MarketplacePurchaseRepository purchaseRepository;
    @InjectMocks MarketplaceVersionAccessService service;

    @Test
    void grantsReadAccessToActiveEntitlementOwner() {
        MarketplacePackVersion version = version(2, null);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(true);

        assertThat(service.requireAccess("buyer", version.getVersionId())).isSameAs(version);
        verify(purchaseRepository, never()).existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", version.getVersionId(), MarketplacePurchaseStatus.ACTIVE);
    }

    @Test
    void grantsReadAccessToLegacyOwnerOnlyForMappedVersionOne() {
        UUID legacyItemId = UUID.randomUUID();
        MarketplacePackVersion version = version(1, legacyItemId);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(false);
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", legacyItemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(true);

        assertThat(service.requireAccess("buyer", version.getVersionId())).isSameAs(version);
    }

    @Test
    void rejectsLegacyPurchaseForVersionTwo() {
        MarketplacePackVersion version = version(2, UUID.randomUUID());
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        assertForbidden(() -> service.requireAccess("buyer", version.getVersionId()));
        verify(purchaseRepository, never()).existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", version.getLegacyItemId(), MarketplacePurchaseStatus.ACTIVE);
    }

    @Test
    void locksActiveEntitlementForWriteAccess() {
        MarketplacePackVersion version = version(2, null);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(entitlementRepository.findByBuyerAndVersionAndStatusForUpdate(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE))
                .thenReturn(Optional.of(new MarketplaceEntitlement()));

        assertThat(service.requireAndLockAccess("buyer", version.getVersionId())).isSameAs(version);
        verify(purchaseRepository, never()).findByBuyerAndItemAndStatusForUpdate(
                "buyer", version.getVersionId(), MarketplacePurchaseStatus.ACTIVE);
    }

    @Test
    void rejectsMissingVersionWithTypedError() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAccess("buyer", versionId))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    private MarketplacePackVersion version(int versionNo, UUID legacyItemId) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setVersionNo(versionNo);
        version.setLegacyItemId(legacyItemId);
        return version;
    }
}
