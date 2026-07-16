package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void legacyPurchaseGrantsAccessWithoutRequiringVersionMapping() {
        UUID itemId = UUID.randomUUID();
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", itemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(true);

        MarketplaceOwnershipService.Ownership ownership = service.findActiveOwnership("buyer", itemId).orElseThrow();

        assertThat(ownership.source()).isEqualTo(MarketplaceOwnershipService.Source.LEGACY_PURCHASE);
        assertThat(ownership.packVersion()).isNull();
        verifyNoInteractions(packVersionService, entitlementRepository);
    }

    @Test
    void entitlementGrantsAccessWhenLegacyPurchaseDoesNotExist() {
        UUID itemId = UUID.randomUUID();
        MarketplacePackVersion version = version();
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", itemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(false);
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(true);

        MarketplaceOwnershipService.Ownership ownership = service.findActiveOwnership("buyer", itemId).orElseThrow();

        assertThat(ownership.source()).isEqualTo(MarketplaceOwnershipService.Source.ENTITLEMENT);
        assertThat(ownership.packVersion()).isSameAs(version);
    }

    @Test
    void anotherUsersEntitlementDoesNotGrantAccess() {
        UUID itemId = UUID.randomUUID();
        MarketplacePackVersion version = version();
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", itemId, MarketplacePurchaseStatus.ACTIVE)).thenReturn(false);
        when(packVersionService.findByItemId(itemId)).thenReturn(Optional.of(version));
        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                "buyer", version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE)).thenReturn(false);

        assertThat(service.findActiveOwnership("buyer", itemId)).isEmpty();
    }

    private MarketplacePackVersion version() {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setPack(pack);
        return version;
    }
}
