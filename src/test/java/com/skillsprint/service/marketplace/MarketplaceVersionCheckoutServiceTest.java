package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.skillsprint.dto.request.marketplace.PurchaseMarketplacePackVersionRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionPurchaseResponse;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.PlatformRevenueEntry;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceSaleStatus;
import com.skillsprint.enums.marketplace.MarketplaceSettlementStatus;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.MarketplaceCheckoutMapper;
import com.skillsprint.repository.CreatorEarningEntryRepository;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplacePurchaseRepository;
import com.skillsprint.repository.MarketplaceSaleRepository;
import com.skillsprint.repository.MarketplaceSaleSettlementRepository;
import com.skillsprint.repository.PlatformRevenueEntryRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceVersionCheckoutServiceTest {

    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplacePurchaseRepository purchaseRepository;
    @Mock MarketplaceSaleRepository saleRepository;
    @Mock MarketplaceEntitlementRepository entitlementRepository;
    @Mock MarketplaceSaleSettlementRepository settlementRepository;
    @Mock CreatorEarningEntryRepository earningEntryRepository;
    @Mock PlatformRevenueEntryRepository platformRevenueEntryRepository;
    @Mock UserRepository userRepository;
    @Mock UserWalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock MarketplaceCheckoutAuditService checkoutAuditService;

    private MarketplaceVersionCheckoutService service;
    private MarketplacePackVersion version;
    private User buyer;
    private UserWallet wallet;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        service = new MarketplaceVersionCheckoutService(
                versionRepository,
                purchaseRepository,
                saleRepository,
                entitlementRepository,
                settlementRepository,
                earningEntryRepository,
                platformRevenueEntryRepository,
                userRepository,
                walletRepository,
                walletTransactionRepository,
                new MarketplaceCheckoutMapper(),
                checkoutAuditService);

        versionId = UUID.randomUUID();
        User creator = user("creator", "creator@example.com");
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        pack.setCreator(creator);
        version = new MarketplacePackVersion();
        version.setVersionId(versionId);
        version.setPack(pack);
        version.setVersionNo(1);
        version.setLegacyItemId(UUID.randomUUID());
        version.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setTitle("Java Pack");
        version.setSubject("Java");
        version.setPriceCoins(100);
        version.setChapterCount(1);
        version.setQuizCount(1);
        version.setQuestionCount(10);
        version.setSaleable(true);

        buyer = user("buyer", "buyer@example.com");
        wallet = new UserWallet();
        wallet.setUser(buyer);
        wallet.setBalance(500);

        lenient().when(versionRepository.findByVersionIdForUpdate(versionId)).thenReturn(Optional.of(version));
        lenient().when(userRepository.findById(buyer.getUserId())).thenReturn(Optional.of(buyer));
        lenient().when(walletRepository.findByUserIdForUpdate(buyer.getUserId())).thenReturn(Optional.of(wallet));
        lenient().when(saleRepository.findByBuyerUserIdAndIdempotencyKey(eq(buyer.getUserId()), any()))
                .thenReturn(Optional.empty());
        lenient().when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyer.getUserId(), versionId, MarketplaceEntitlementStatus.ACTIVE)).thenReturn(false);
        lenient().when(saleRepository.save(any(MarketplaceSale.class))).thenAnswer(invocation -> {
            MarketplaceSale sale = invocation.getArgument(0);
            sale.setSaleId(UUID.randomUUID());
            return sale;
        });
        lenient().when(entitlementRepository.save(any(MarketplaceEntitlement.class))).thenAnswer(invocation -> {
            MarketplaceEntitlement entitlement = invocation.getArgument(0);
            entitlement.setEntitlementId(UUID.randomUUID());
            return entitlement;
        });
        lenient().when(settlementRepository.save(any(MarketplaceSaleSettlement.class))).thenAnswer(invocation -> {
            MarketplaceSaleSettlement settlement = invocation.getArgument(0);
            settlement.setSettlementId(UUID.randomUUID());
            return settlement;
        });
    }

    @Test
    void debitsBuyerAndRecordsOneCompleteSettlement() {
        MarketplaceVersionPurchaseResponse response = service.purchaseWithCoins(
                buyer.getUserId(), versionId, request("checkout-1"));

        assertThat(wallet.getBalance()).isEqualTo(400);
        assertThat(response.getPackVersionId()).isEqualTo(versionId);
        assertThat(response.getGrossCoinAmount()).isEqualTo(100);
        assertThat(response.getCreatorAmount()).isEqualTo(80);
        assertThat(response.getPlatformAmount()).isEqualTo(20);
        assertThat(response.getRemainingCoinBalance()).isEqualTo(400);

        ArgumentCaptor<MarketplaceSale> saleCaptor = ArgumentCaptor.forClass(MarketplaceSale.class);
        verify(saleRepository).save(saleCaptor.capture());
        assertThat(saleCaptor.getValue().getStatus()).isEqualTo(MarketplaceSaleStatus.COMPLETED);
        assertThat(saleCaptor.getValue().getGrossVndAmount()).isEqualTo(100L);
        assertThat(saleCaptor.getValue().getCoinToVndRate()).isEqualByComparingTo(new BigDecimal("1.0000"));

        ArgumentCaptor<MarketplaceEntitlement> entitlementCaptor = ArgumentCaptor.forClass(MarketplaceEntitlement.class);
        verify(entitlementRepository).save(entitlementCaptor.capture());
        assertThat(entitlementCaptor.getValue().getStatus()).isEqualTo(MarketplaceEntitlementStatus.ACTIVE);

        ArgumentCaptor<MarketplaceSaleSettlement> settlementCaptor = ArgumentCaptor.forClass(MarketplaceSaleSettlement.class);
        verify(settlementRepository).save(settlementCaptor.capture());
        assertThat(settlementCaptor.getValue().getCreatorShareBps()).isEqualTo(8_000);
        assertThat(settlementCaptor.getValue().getPlatformShareBps()).isEqualTo(2_000);
        assertThat(settlementCaptor.getValue().getStatus()).isEqualTo(MarketplaceSettlementStatus.RECORDED);

        ArgumentCaptor<CreatorEarningEntry> earningCaptor = ArgumentCaptor.forClass(CreatorEarningEntry.class);
        verify(earningEntryRepository).save(earningCaptor.capture());
        assertThat(earningCaptor.getValue().getAmount()).isEqualTo(80);
        assertThat(earningCaptor.getValue().getState()).isEqualTo(CreatorEarningState.PENDING);

        ArgumentCaptor<PlatformRevenueEntry> revenueCaptor = ArgumentCaptor.forClass(PlatformRevenueEntry.class);
        verify(platformRevenueEntryRepository).save(revenueCaptor.capture());
        assertThat(revenueCaptor.getValue().getAmount()).isEqualTo(20);

        ArgumentCaptor<WalletTransaction> transactionCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getReferenceType()).isEqualTo(WalletTransactionReferenceType.MARKETPLACE_SALE);
        assertThat(transactionCaptor.getValue().getBalanceAfter()).isEqualTo(400);
        verify(checkoutAuditService).recordCompletedCheckout(saleCaptor.getValue(), settlementCaptor.getValue());
    }

    @Test
    void replaysExistingSaleWithoutDebitingAgain() {
        MarketplaceSale sale = sale("checkout-retry");
        MarketplaceEntitlement entitlement = new MarketplaceEntitlement();
        entitlement.setEntitlementId(UUID.randomUUID());
        entitlement.setSourceSale(sale);
        MarketplaceSaleSettlement settlement = settlement(sale);

        when(saleRepository.findByBuyerUserIdAndIdempotencyKey(buyer.getUserId(), "checkout-retry"))
                .thenReturn(Optional.of(sale));
        when(entitlementRepository.findBySourceSaleSaleId(sale.getSaleId())).thenReturn(Optional.of(entitlement));
        when(settlementRepository.findBySaleSaleId(sale.getSaleId())).thenReturn(Optional.of(settlement));
        WalletTransaction transaction = new WalletTransaction();
        transaction.setBalanceAfter(400);
        when(walletTransactionRepository.findByReferenceTypeAndReferenceId(
                WalletTransactionReferenceType.MARKETPLACE_SALE, sale.getSaleId())).thenReturn(Optional.of(transaction));

        MarketplaceVersionPurchaseResponse response = service.purchaseWithCoins(
                buyer.getUserId(), versionId, request("checkout-retry"));

        assertThat(response.getSaleId()).isEqualTo(sale.getSaleId());
        assertThat(wallet.getBalance()).isEqualTo(500);
        verify(walletRepository, never()).save(any());
        verify(earningEntryRepository, never()).save(any());
        verify(platformRevenueEntryRepository, never()).save(any());
        verify(checkoutAuditService, never()).recordCompletedCheckout(any(), any());
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForAnotherVersion() {
        MarketplaceSale sale = sale("checkout-reused");
        when(saleRepository.findByBuyerUserIdAndIdempotencyKey(buyer.getUserId(), "checkout-reused"))
                .thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.purchaseWithCoins(
                buyer.getUserId(), UUID.randomUUID(), request("checkout-reused")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_CHECKOUT_IDEMPOTENCY_CONFLICT);

        verify(walletRepository, never()).save(any());
    }

    @Test
    void upgradesFromAnEarlierEntitlementAndSnapshotsZeroDiscountPolicy() {
        UUID targetVersionId = UUID.randomUUID();
        MarketplacePackVersion targetVersion = new MarketplacePackVersion();
        targetVersion.setVersionId(targetVersionId);
        targetVersion.setPack(version.getPack());
        targetVersion.setVersionNo(2);
        targetVersion.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        targetVersion.setPriceCoins(200);
        targetVersion.setSaleable(true);
        MarketplaceEntitlement sourceEntitlement = new MarketplaceEntitlement();
        sourceEntitlement.setEntitlementId(UUID.randomUUID());
        sourceEntitlement.setBuyer(buyer);
        sourceEntitlement.setPackVersion(version);
        sourceEntitlement.setStatus(MarketplaceEntitlementStatus.ACTIVE);

        when(versionRepository.findByVersionIdForUpdate(targetVersionId)).thenReturn(Optional.of(targetVersion));
        when(entitlementRepository
                .findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdAndPackVersionVersionNoLessThanOrderByPackVersionVersionNoDesc(
                        buyer.getUserId(),
                        MarketplaceEntitlementStatus.ACTIVE,
                        version.getPack().getPackId(),
                        2))
                .thenReturn(Optional.of(sourceEntitlement));

        MarketplaceVersionPurchaseResponse response = service.upgradeWithCoins(
                buyer.getUserId(), targetVersionId, request("upgrade-1"));

        assertThat(wallet.getBalance()).isEqualTo(300);
        assertThat(response.getUpgrade()).isTrue();
        assertThat(response.getOriginalGrossCoinAmount()).isEqualTo(200);
        assertThat(response.getDiscountCoinAmount()).isZero();
        assertThat(response.getGrossCoinAmount()).isEqualTo(200);

        ArgumentCaptor<MarketplaceSale> saleCaptor = ArgumentCaptor.forClass(MarketplaceSale.class);
        verify(saleRepository).save(saleCaptor.capture());
        assertThat(saleCaptor.getValue().getSourceEntitlement()).isSameAs(sourceEntitlement);
        assertThat(saleCaptor.getValue().getOriginalGrossCoinAmount()).isEqualTo(200);
        assertThat(saleCaptor.getValue().getDiscountCoinAmount()).isZero();
    }

    @Test
    void upgradeRequiresAnEarlierEntitlementForTheSamePack() {
        UUID targetVersionId = UUID.randomUUID();
        MarketplacePackVersion targetVersion = new MarketplacePackVersion();
        targetVersion.setVersionId(targetVersionId);
        targetVersion.setPack(version.getPack());
        targetVersion.setVersionNo(2);
        targetVersion.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        targetVersion.setPriceCoins(200);
        targetVersion.setSaleable(true);
        when(versionRepository.findByVersionIdForUpdate(targetVersionId)).thenReturn(Optional.of(targetVersion));
        when(entitlementRepository
                .findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdAndPackVersionVersionNoLessThanOrderByPackVersionVersionNoDesc(
                        buyer.getUserId(),
                        MarketplaceEntitlementStatus.ACTIVE,
                        version.getPack().getPackId(),
                        2))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upgradeWithCoins(buyer.getUserId(), targetVersionId, request("upgrade-no-source")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_UPGRADE_SOURCE_ENTITLEMENT_NOT_FOUND);

        verify(saleRepository, never()).save(any());
    }

    @Test
    void rejectsVersionsThatAreNotForSale() {
        version.setSaleable(false);

        assertThatThrownBy(() -> service.purchaseWithCoins(buyer.getUserId(), versionId, request("checkout-hidden")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_SALEABLE);

        verify(walletRepository, never()).save(any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void rejectsInsufficientCoinBeforeAnySettlementWrite() {
        wallet.setBalance(99);

        assertThatThrownBy(() -> service.purchaseWithCoins(buyer.getUserId(), versionId, request("checkout-low")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.WALLET_INSUFFICIENT_BALANCE);

        verify(saleRepository, never()).save(any());
        verify(entitlementRepository, never()).save(any());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void rejectsCreatorPurchaseAndExistingEntitlement() {
        when(userRepository.findById("creator")).thenReturn(Optional.of(version.getPack().getCreator()));
        assertThatThrownBy(() -> service.purchaseWithCoins("creator", versionId, request("self")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_CREATOR_CANNOT_PURCHASE);

        when(entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyer.getUserId(), versionId, MarketplaceEntitlementStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> service.purchaseWithCoins(buyer.getUserId(), versionId, request("owned")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_ENTITLEMENT_ALREADY_EXISTS);
    }

    @Test
    void rejectsAnActiveLegacyPurchaseBeforeDebitingAgain() {
        when(purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(
                buyer.getUserId(), version.getLegacyItemId(), com.skillsprint.enums.marketplace.MarketplacePurchaseStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.purchaseWithCoins(buyer.getUserId(), versionId, request("legacy-owned")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_ENTITLEMENT_ALREADY_EXISTS);

        verify(walletRepository, never()).save(any());
        verify(saleRepository, never()).save(any());
    }

    @Test
    void rejectsAVisibleButNonPublishedVersion() {
        version.setStatus(MarketplacePackVersionStatus.DRAFT);
        version.setSaleable(true);

        assertThatThrownBy(() -> service.purchaseWithCoins(buyer.getUserId(), versionId, request("draft-version")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_SALEABLE);

        verify(walletRepository, never()).save(any());
    }

    private MarketplaceSale sale(String idempotencyKey) {
        MarketplaceSale sale = new MarketplaceSale();
        sale.setSaleId(UUID.randomUUID());
        sale.setBuyer(buyer);
        sale.setPack(version.getPack());
        sale.setPackVersion(version);
        sale.setGrossCoinAmount(100);
        sale.setGrossVndAmount(100L);
        sale.setCoinToVndRate(new BigDecimal("1.0000"));
        sale.setStatus(MarketplaceSaleStatus.COMPLETED);
        sale.setIdempotencyKey(idempotencyKey);
        return sale;
    }

    private MarketplaceSaleSettlement settlement(MarketplaceSale sale) {
        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setSettlementId(UUID.randomUUID());
        settlement.setSale(sale);
        settlement.setCreator(version.getPack().getCreator());
        settlement.setCreatorAmount(80);
        settlement.setPlatformAmount(20);
        return settlement;
    }

    private PurchaseMarketplacePackVersionRequest request(String idempotencyKey) {
        PurchaseMarketplacePackVersionRequest request = new PurchaseMarketplacePackVersionRequest();
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private User user(String userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(userId);
        return user;
    }
}
