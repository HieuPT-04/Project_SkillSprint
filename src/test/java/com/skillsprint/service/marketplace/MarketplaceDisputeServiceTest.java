package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.marketplace.CreateMarketplaceDisputeRequest;
import com.skillsprint.dto.request.marketplace.DecideMarketplaceDisputeRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeEligibilityResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeResponse;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRefundDispute;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.MarketplaceDisputeReason;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplaceSaleStatus;
import com.skillsprint.enums.marketplace.MarketplaceSettlementStatus;
import com.skillsprint.enums.marketplace.WalletTransactionDirection;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CreatorEarningEntryRepository;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplaceRefundDisputeRepository;
import com.skillsprint.repository.MarketplaceSaleRepository;
import com.skillsprint.repository.MarketplaceSaleSettlementRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceDisputeServiceTest {

    @Mock MarketplaceRefundDisputeRepository disputeRepository;
    @Mock MarketplaceSaleRepository saleRepository;
    @Mock MarketplaceEntitlementRepository entitlementRepository;
    @Mock MarketplaceSaleSettlementRepository settlementRepository;
    @Mock CreatorEarningEntryRepository earningRepository;
    @Mock UserWalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock UserRepository userRepository;
    @Mock PlatformTreasuryService platformTreasuryService;
    @Mock MarketplaceDisputeAuditService disputeAuditService;

    MarketplaceDisputeService service;
    User buyer;
    User admin;
    MarketplacePackVersion version;
    MarketplaceSale sale;

    @BeforeEach
    void setUp() {
        service = new MarketplaceDisputeService(
                disputeRepository, saleRepository, entitlementRepository, settlementRepository,
                earningRepository, walletRepository, walletTransactionRepository, userRepository, platformTreasuryService, disputeAuditService);
        buyer = user("buyer", "Buyer");
        admin = user("admin", "Admin");
        version = version();
        sale = sale(buyer, version, 100, MarketplaceSaleStatus.COMPLETED);
    }

    @Test
    void buyerCannotDisputeAnotherBuyersSale() {
        CreateMarketplaceDisputeRequest request = createRequest(sale.getSaleId());
        when(saleRepository.findByIdForUpdate(sale.getSaleId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.createDispute("intruder", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_DISPUTE_NOT_ELIGIBLE));
        verify(disputeRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateActiveDisputeIsRejected() {
        CreateMarketplaceDisputeRequest request = createRequest(sale.getSaleId());
        when(saleRepository.findByIdForUpdate(sale.getSaleId())).thenReturn(Optional.of(sale));
        when(disputeRepository.existsActiveForSale(sale.getSaleId())).thenReturn(true);

        assertThatThrownBy(() -> service.createDispute("buyer", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_DISPUTE_ALREADY_ACTIVE));
    }

    @Test
    void eligibilityReflectsAlreadyRefundedSale() {
        sale.setStatus(MarketplaceSaleStatus.REFUNDED);
        when(saleRepository.findById(sale.getSaleId())).thenReturn(Optional.of(sale));
        when(disputeRepository.findByBuyerUserIdOrderByCreatedAtDesc("buyer")).thenReturn(java.util.List.of());

        MarketplaceDisputeEligibilityResponse response = service.getEligibility("buyer", sale.getSaleId());

        assertThat(response.isEligible()).isFalse();
        assertThat(response.getIneligibilityReason()).isEqualTo("ALREADY_REFUNDED");
    }

    @Test
    void approvingRequiresADecisionNote() {
        MarketplaceRefundDispute dispute = dispute(MarketplaceDisputeStatus.OPEN);
        when(disputeRepository.findByIdForUpdate(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        DecideMarketplaceDisputeRequest request = new DecideMarketplaceDisputeRequest();
        request.setStatus(MarketplaceDisputeStatus.APPROVED);
        request.setDecisionNote("   ");

        assertThatThrownBy(() -> service.decide("admin", dispute.getDisputeId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_DISPUTE_DECISION_NOTE_REQUIRED));
    }

    @Test
    void refundOfAnUnapprovedDisputeIsRejected() {
        MarketplaceRefundDispute dispute = dispute(MarketplaceDisputeStatus.OPEN);
        when(disputeRepository.findByIdForUpdate(dispute.getDisputeId())).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.completeRefund("admin", dispute.getDisputeId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REFUND_NOT_APPROVED));
        verify(saleRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void refundIsIdempotentForAnAlreadyRefundedDispute() {
        MarketplaceRefundDispute dispute = dispute(MarketplaceDisputeStatus.REFUNDED);
        when(disputeRepository.findByIdForUpdate(dispute.getDisputeId())).thenReturn(Optional.of(dispute));

        MarketplaceDisputeResponse response = service.completeRefund("admin", dispute.getDisputeId());

        assertThat(response.getStatus()).isEqualTo(MarketplaceDisputeStatus.REFUNDED);
        verify(saleRepository, never()).findByIdForUpdate(any());
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    void refundIsBlockedWhenCreatorEarningIsAlreadyPaid() {
        MarketplaceRefundDispute dispute = dispute(MarketplaceDisputeStatus.APPROVED);
        MarketplaceSaleSettlement settlement = settlement();
        CreatorEarningEntry earning = earning(settlement, CreatorEarningState.PAID, null);
        when(disputeRepository.findByIdForUpdate(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(saleRepository.findByIdForUpdate(sale.getSaleId())).thenReturn(Optional.of(sale));
        when(settlementRepository.findBySaleSaleId(sale.getSaleId())).thenReturn(Optional.of(settlement));
        when(earningRepository.findBySettlementIdForUpdate(settlement.getSettlementId())).thenReturn(Optional.of(earning));

        assertThatThrownBy(() -> service.completeRefund("admin", dispute.getDisputeId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REFUND_EARNING_LOCKED));
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    void refundCompletesWithCompensatingRecords() {
        MarketplaceRefundDispute dispute = dispute(MarketplaceDisputeStatus.APPROVED);
        MarketplaceSaleSettlement settlement = settlement();
        CreatorEarningEntry earning = earning(settlement, CreatorEarningState.PENDING, null);
        MarketplaceEntitlement entitlement = entitlement();
        UserWallet wallet = wallet(50);
        UUID txnId = UUID.randomUUID();

        when(disputeRepository.findByIdForUpdate(dispute.getDisputeId())).thenReturn(Optional.of(dispute));
        when(saleRepository.findByIdForUpdate(sale.getSaleId())).thenReturn(Optional.of(sale));
        when(settlementRepository.findBySaleSaleId(sale.getSaleId())).thenReturn(Optional.of(settlement));
        when(earningRepository.findBySettlementIdForUpdate(settlement.getSettlementId())).thenReturn(Optional.of(earning));
        when(walletRepository.findByUserIdForUpdate("buyer")).thenReturn(Optional.of(wallet));
        when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        when(walletTransactionRepository.saveAndFlush(any(WalletTransaction.class)))
                .thenAnswer(invocation -> {
                    WalletTransaction transaction = invocation.getArgument(0);
                    transaction.setTransactionId(txnId);
                    return transaction;
                });
        when(entitlementRepository.findBySourceSaleSaleId(sale.getSaleId())).thenReturn(Optional.of(entitlement));
        when(disputeRepository.saveAndFlush(dispute)).thenReturn(dispute);

        MarketplaceDisputeResponse response = service.completeRefund("admin", dispute.getDisputeId());

        assertThat(response.getStatus()).isEqualTo(MarketplaceDisputeStatus.REFUNDED);
        assertThat(response.getRefundCoinAmount()).isEqualTo(100);
        assertThat(response.getRefundWalletTransactionId()).isEqualTo(txnId);
        assertThat(wallet.getBalance()).isEqualTo(150);
        assertThat(settlement.getStatus()).isEqualTo(MarketplaceSettlementStatus.REVERSED);
        assertThat(earning.getState()).isEqualTo(CreatorEarningState.REVERSED);
        assertThat(entitlement.getStatus()).isEqualTo(MarketplaceEntitlementStatus.REVOKED);
        assertThat(entitlement.getRevokedAt()).isNotNull();
        assertThat(sale.getStatus()).isEqualTo(MarketplaceSaleStatus.REFUNDED);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDirection()).isEqualTo(WalletTransactionDirection.CREDIT);
        assertThat(captor.getValue().getReferenceType()).isEqualTo(WalletTransactionReferenceType.MARKETPLACE_REFUND);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(sale.getSaleId());
    }

    private CreateMarketplaceDisputeRequest createRequest(UUID saleId) {
        CreateMarketplaceDisputeRequest request = new CreateMarketplaceDisputeRequest();
        request.setSaleId(saleId);
        request.setReason(MarketplaceDisputeReason.NOT_AS_DESCRIBED);
        request.setDescription("problem");
        return request;
    }

    private MarketplaceRefundDispute dispute(MarketplaceDisputeStatus status) {
        MarketplaceRefundDispute dispute = new MarketplaceRefundDispute();
        dispute.setDisputeId(UUID.randomUUID());
        dispute.setSale(sale);
        dispute.setBuyer(buyer);
        dispute.setPackVersion(version);
        dispute.setReason(MarketplaceDisputeReason.NOT_AS_DESCRIBED);
        dispute.setStatus(status);
        return dispute;
    }

    private MarketplaceSaleSettlement settlement() {
        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setSettlementId(UUID.randomUUID());
        settlement.setSale(sale);
        settlement.setStatus(MarketplaceSettlementStatus.RECORDED);
        settlement.setCreatorAmount(80);
        settlement.setPlatformAmount(20);
        return settlement;
    }

    private CreatorEarningEntry earning(MarketplaceSaleSettlement settlement, CreatorEarningState state, CreatorPayout payout) {
        CreatorEarningEntry earning = new CreatorEarningEntry();
        earning.setEarningEntryId(UUID.randomUUID());
        earning.setSettlement(settlement);
        earning.setState(state);
        earning.setAmount(80);
        earning.setPayout(payout);
        return earning;
    }

    private MarketplaceEntitlement entitlement() {
        MarketplaceEntitlement entitlement = new MarketplaceEntitlement();
        entitlement.setEntitlementId(UUID.randomUUID());
        entitlement.setBuyer(buyer);
        entitlement.setPackVersion(version);
        entitlement.setStatus(MarketplaceEntitlementStatus.ACTIVE);
        return entitlement;
    }

    private UserWallet wallet(int balance) {
        UserWallet wallet = new UserWallet();
        wallet.setUser(buyer);
        wallet.setBalance(balance);
        return wallet;
    }

    private MarketplaceSale sale(User buyer, MarketplacePackVersion version, int amount, MarketplaceSaleStatus status) {
        MarketplaceSale sale = new MarketplaceSale();
        sale.setSaleId(UUID.randomUUID());
        sale.setBuyer(buyer);
        sale.setPack(version.getPack());
        sale.setPackVersion(version);
        sale.setGrossCoinAmount(amount);
        sale.setStatus(status);
        return sale;
    }

    private MarketplacePackVersion version() {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setPack(pack);
        version.setVersionNo(1);
        version.setTitle("Pack");
        return version;
    }

    private User user(String id, String name) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(name);
        return user;
    }
}
