package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.response.marketplace.MarketplaceAdminItemDetailResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceCatalogItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemDetailResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplacePurchaseResponse;
import com.skillsprint.dto.response.marketplace.PurchasedQuizPackResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplacePurchase;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplacePurchaseStatus;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The item-based endpoints keep working and keep returning {@code itemId} as the
 * compatibility identifier, while additively exposing the matching {@code packId},
 * {@code versionId}, and {@code versionNo} of the version migrated for that item.
 */
@ExtendWith(MockitoExtension.class)
class MarketplacePackVersionCompatibilityTest {

    @Mock MarketplaceItemRepository itemRepository;
    @Mock MarketplaceQuizPackSnapshotRepository snapshotRepository;
    @Mock MarketplaceReviewRepository reviewRepository;
    @Mock MarketplacePurchaseRepository purchaseRepository;
    @Mock UserRepository userRepository;
    @Mock UserWalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock MarketplacePackVersionService packVersionService;

    MarketplaceItem item;
    MarketplaceQuizPackSnapshot snapshot;
    MarketplacePackVersion version;
    MarketplacePackVersionIdentity identity;

    @BeforeEach
    void setUp() {
        item = item(MarketplaceItemStatus.PUBLISHED);
        snapshot = snapshot(item);
        version = version(item);
        identity = MarketplacePackVersionIdentity.of(version);

        lenient().when(packVersionService.identityOf(item.getItemId())).thenReturn(identity);
        lenient().when(packVersionService.identitiesOf(any()))
                .thenReturn(Map.of(item.getItemId(), identity));
        lenient().when(packVersionService.findByItemId(item.getItemId())).thenReturn(Optional.of(version));
        lenient().when(snapshotRepository.findByItemItemId(item.getItemId())).thenReturn(Optional.of(snapshot));
    }

    @Test
    void catalogListExposesItemIdAndVersionOneIdentity() {
        MarketplaceCatalogService service = new MarketplaceCatalogService(
                itemRepository, snapshotRepository, reviewRepository, packVersionService);
        when(itemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PUBLISHED))
                .thenReturn(List.of(item));
        when(reviewRepository.findByItemItemId(item.getItemId())).thenReturn(List.of());

        MarketplaceCatalogItemResponse response = service.getPublishedItems(null).get(0);

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    @Test
    void catalogDetailExposesItemIdAndVersionOneIdentity() {
        MarketplaceCatalogService service = new MarketplaceCatalogService(
                itemRepository, snapshotRepository, reviewRepository, packVersionService);
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(reviewRepository.findByItemItemId(item.getItemId())).thenReturn(List.of());

        MarketplaceItemDetailResponse response = service.getPublishedItem(item.getItemId());

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    @Test
    void adminItemDetailExposesItemIdAndVersionOneIdentity() {
        MarketplaceAdminService service = new MarketplaceAdminService(
                itemRepository, snapshotRepository, packVersionService, userRepository);
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));

        MarketplaceAdminItemDetailResponse response = service.getItemDetail(item.getItemId());

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    @Test
    void adminItemListExposesItemIdAndVersionOneIdentity() {
        MarketplaceAdminService service = new MarketplaceAdminService(
                itemRepository, snapshotRepository, packVersionService, userRepository);
        when(itemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PUBLISHED))
                .thenReturn(List.of(item));

        MarketplaceItemResponse response = service.getItems(MarketplaceItemStatus.PUBLISHED).get(0);

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    @Test
    void purchasedLibraryListExposesItemIdAndVersionOneIdentity() {
        MarketplaceLibraryService service = new MarketplaceLibraryService(
                purchaseRepository, snapshotRepository, packVersionService);
        when(purchaseRepository.findByUserUserIdAndStatusOrderByPurchasedAtDesc(
                "buyer", MarketplacePurchaseStatus.ACTIVE)).thenReturn(List.of(purchase()));

        MarketplaceCatalogItemResponse response = service.getMyPacks("buyer").get(0);

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    @Test
    void purchasedPackDetailExposesItemIdAndVersionOneIdentity() {
        MarketplaceLibraryService service = new MarketplaceLibraryService(
                purchaseRepository, snapshotRepository, packVersionService);
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                "buyer", item.getItemId(), MarketplacePurchaseStatus.ACTIVE)).thenReturn(true);

        PurchasedQuizPackResponse response = service.getMyPack("buyer", item.getItemId());

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    @Test
    void coinPurchaseExposesItemIdAndVersionOneIdentityAndLinksTheVersion() {
        MarketplacePurchaseService service = new MarketplacePurchaseService(
                itemRepository, purchaseRepository, userRepository, walletRepository,
                walletTransactionRepository, packVersionService);
        User buyer = new User();
        buyer.setUserId("buyer");
        UserWallet wallet = new UserWallet();
        wallet.setUser(buyer);
        wallet.setBalance(500);
        when(itemRepository.findById(item.getItemId())).thenReturn(Optional.of(item));
        when(purchaseRepository.existsByUserUserIdAndItemItemId("buyer", item.getItemId())).thenReturn(false);
        when(userRepository.findById("buyer")).thenReturn(Optional.of(buyer));
        when(walletRepository.findByUserIdForUpdate("buyer")).thenReturn(Optional.of(wallet));
        when(purchaseRepository.save(any(MarketplacePurchase.class))).thenAnswer(invocation -> {
            MarketplacePurchase saved = invocation.getArgument(0);
            saved.setPurchaseId(UUID.randomUUID());
            return saved;
        });

        MarketplacePurchaseResponse response = service.purchaseWithCoins("buyer", item.getItemId());

        assertIdentity(response.getItemId(), response.getPackId(), response.getVersionId(), response.getVersionNo());
    }

    private void assertIdentity(UUID itemId, UUID packId, UUID versionId, Integer versionNo) {
        assertThat(itemId).isEqualTo(item.getItemId());
        assertThat(packId).isEqualTo(version.getPack().getPackId());
        assertThat(versionId).isEqualTo(version.getVersionId());
        assertThat(versionNo).isEqualTo(1);
    }

    private MarketplacePurchase purchase() {
        MarketplacePurchase purchase = new MarketplacePurchase();
        purchase.setItem(item);
        purchase.setPriceCoins(100);
        purchase.setStatus(MarketplacePurchaseStatus.ACTIVE);
        purchase.setPurchasedAt(Instant.now());
        return purchase;
    }

    private MarketplacePackVersion version(MarketplaceItem item) {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        pack.setLegacyItemId(item.getItemId());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setPack(pack);
        version.setVersionNo(1);
        version.setLegacyItemId(item.getItemId());
        version.setSaleable(true);
        return version;
    }

    private MarketplaceItem item(MarketplaceItemStatus status) {
        User creator = new User();
        creator.setUserId("creator");
        creator.setFullName("Creator");
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        MarketplaceItem item = new MarketplaceItem();
        item.setItemId(UUID.randomUUID());
        item.setCreator(creator);
        item.setSourceWorkspace(workspace);
        item.setTitle("Pack");
        item.setSubject("Toan");
        item.setPriceCoins(100);
        item.setStatus(status);
        item.setPublishedAt(Instant.now());
        return item;
    }

    private MarketplaceQuizPackSnapshot snapshot(MarketplaceItem item) {
        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setItem(item);
        snapshot.setChapterCount(4);
        snapshot.setQuizCount(4);
        snapshot.setQuestionCount(20);
        snapshot.setContent(new ObjectMapper().createObjectNode());
        return snapshot;
    }
}
