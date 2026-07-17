package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class MarketplaceRankedQuizAccessServiceTest {

    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplaceEntitlementRepository entitlementRepository;
    @Mock MarketplacePurchaseRepository purchaseRepository;
    @InjectMocks MarketplaceRankedQuizAccessService service;

    @Test
    void grantsAccessToActiveEntitlementOwner() {
        MarketplacePackVersion version = version(2, null);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(true);

        assertThat(service.requireRankedAccess("buyer", version.getVersionId())).isSameAs(version);

        verify(purchaseRepository, never()).existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", version.getVersionId(), MarketplacePurchaseStatus.ACTIVE);
    }

    @Test
    void grantsLegacyPurchaseOwnerAccessOnlyToMappedVersionOne() {
        UUID legacyItemId = UUID.randomUUID();
        MarketplacePackVersion version = version(1, legacyItemId);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(false);
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", legacyItemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(true);

        assertThat(service.requireRankedAccess("buyer", version.getVersionId())).isSameAs(version);
    }

    @Test
    void rejectsLegacyPurchaseForVersionTwoEvenWhenLegacyIdIsPresent() {
        UUID legacyItemId = UUID.randomUUID();
        MarketplacePackVersion version = version(2, legacyItemId);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.requireRankedAccess("buyer", version.getVersionId()))
                .isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));

        verify(purchaseRepository, never()).existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", legacyItemId, MarketplacePurchaseStatus.ACTIVE);
    }

    private MarketplacePackVersion version(int versionNo, UUID legacyItemId) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setVersionNo(versionNo);
        version.setLegacyItemId(legacyItemId);
        return version;
    }
}
