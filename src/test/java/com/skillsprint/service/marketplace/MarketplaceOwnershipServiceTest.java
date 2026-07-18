package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceOwnershipServiceTest {

    @Mock MarketplacePurchaseRepository purchaseRepository;
    @Mock MarketplaceEntitlementRepository entitlementRepository;
    @Mock MarketplacePackVersionService packVersionService;
    @InjectMocks MarketplaceOwnershipService service;

    @Test
    void legacyPurchaseStillGrantsAccessWithoutVersionMapping() {
        UUID itemId = UUID.randomUUID();
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.empty());
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", itemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(true);

        MarketplaceOwnershipService.Ownership ownership = service.findActiveOwnership("buyer", itemId).orElseThrow();

        assertThat(ownership.source()).isEqualTo(MarketplaceOwnershipService.Source.LEGACY_PURCHASE);
        assertThat(ownership.packVersion()).isNull();
    }

    @Test
    void latestVersionEntitlementTakesPriorityForMappedPack() {
        UUID itemId = UUID.randomUUID();
        MarketplacePackVersion mappedVersionOne = version(1);
        MarketplacePackVersion entitledVersionTwo = version(2);
        entitledVersionTwo.setPack(mappedVersionOne.getPack());
        MarketplaceEntitlement entitlement = new MarketplaceEntitlement();
        entitlement.setPackVersion(entitledVersionTwo);
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.of(mappedVersionOne));
        when(entitlementRepository
                .findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdOrderByPackVersionVersionNoDesc(
                        "buyer", MarketplaceEntitlementStatus.ACTIVE, mappedVersionOne.getPack().getPackId()))
                .thenReturn(Optional.of(entitlement));

        MarketplaceOwnershipService.Ownership ownership = service.findActiveOwnership("buyer", itemId).orElseThrow();

        assertThat(ownership.source()).isEqualTo(MarketplaceOwnershipService.Source.ENTITLEMENT);
        assertThat(ownership.packVersion()).isSameAs(entitledVersionTwo);
    }

    @Test
    void anotherUsersEntitlementDoesNotGrantAccess() {
        UUID itemId = UUID.randomUUID();
        MarketplacePackVersion mappedVersion = version(1);
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.of(mappedVersion));
        when(entitlementRepository
                .findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdOrderByPackVersionVersionNoDesc(
                        "buyer", MarketplaceEntitlementStatus.ACTIVE, mappedVersion.getPack().getPackId()))
                .thenReturn(Optional.empty());
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", itemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(false);

        assertThat(service.findActiveOwnership("buyer", itemId)).isEmpty();
    }

    private MarketplacePackVersion version(int versionNo) {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setVersionNo(versionNo);
        version.setPack(pack);
        return version;
    }
}
