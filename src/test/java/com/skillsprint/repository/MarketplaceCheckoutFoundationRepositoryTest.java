package com.skillsprint.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.entity.CreatorPayoutDestination;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.PlatformRevenueEntry;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceSaleStatus;
import com.skillsprint.enums.marketplace.MarketplaceSettlementStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.mapper.MarketplaceCheckoutMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MarketplaceCheckoutFoundationRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired StudyWorkspaceRepository workspaceRepository;
    @Autowired MarketplacePackRepository packRepository;
    @Autowired MarketplacePackVersionRepository packVersionRepository;
    @Autowired MarketplaceSaleRepository saleRepository;
    @Autowired MarketplaceEntitlementRepository entitlementRepository;
    @Autowired MarketplaceSaleSettlementRepository settlementRepository;
    @Autowired CreatorEarningEntryRepository earningEntryRepository;
    @Autowired PlatformRevenueEntryRepository platformRevenueEntryRepository;
    @Autowired CreatorPayoutDestinationRepository payoutDestinationRepository;
    @Autowired CreatorPayoutRepository payoutRepository;

    private User creator;
    private User buyer;
    private MarketplacePack pack;
    private MarketplacePackVersion version;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(user("checkout-creator", "creator@example.com", "Creator"));
        buyer = userRepository.save(user("checkout-buyer", "buyer@example.com", "Buyer"));
        StudyWorkspace workspace = workspaceRepository.save(workspace(creator));

        pack = new MarketplacePack();
        pack.setCreator(creator);
        pack.setSourceWorkspace(workspace);
        pack = packRepository.saveAndFlush(pack);

        version = new MarketplacePackVersion();
        version.setPack(pack);
        version.setVersionNo(1);
        version.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setTitle("Java Pack");
        version.setSubject("Java");
        version.setPriceCoins(100);
        version.setChapterCount(1);
        version.setQuizCount(1);
        version.setQuestionCount(10);
        version.setContent(new ObjectMapper().createObjectNode());
        version.setSaleable(true);
        version = packVersionRepository.saveAndFlush(version);
    }

    @Test
    void persistsCheckoutSettlementEarningRevenueAndPayoutAuditGraph() {
        MarketplaceSale sale = saleRepository.saveAndFlush(sale("checkout-1"));

        MarketplaceEntitlement entitlement = new MarketplaceEntitlement();
        entitlement.setBuyer(buyer);
        entitlement.setPackVersion(version);
        entitlement.setSourceSale(sale);
        entitlement.setStatus(MarketplaceEntitlementStatus.ACTIVE);
        entitlement.setGrantedAt(Instant.now());
        entitlement = entitlementRepository.saveAndFlush(entitlement);

        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setSale(sale);
        settlement.setCreator(creator);
        settlement.setCreatorShareBps(8000);
        settlement.setCreatorAmount(80);
        settlement.setPlatformShareBps(2000);
        settlement.setPlatformAmount(20);
        settlement.setCoinToVndRate(new BigDecimal("1.0000"));
        settlement.setStatus(MarketplaceSettlementStatus.RECORDED);
        settlement = settlementRepository.saveAndFlush(settlement);

        CreatorPayoutDestination destination = new CreatorPayoutDestination();
        destination.setCreator(creator);
        destination.setBankName("MBBank");
        destination.setBankCode("MB");
        destination.setAccountHolder("Creator");
        destination.setQrObjectKey("creator-payouts/checkout-creator/qr.png");
        destination = payoutDestinationRepository.saveAndFlush(destination);

        CreatorPayout payout = new CreatorPayout();
        payout.setCreator(creator);
        payout.setDestination(destination);
        payout.setRequestedAmount(80);
        payout.setStatus(CreatorPayoutStatus.REQUESTED);
        payout.setDestinationBankName(destination.getBankName());
        payout.setDestinationBankCode(destination.getBankCode());
        payout.setDestinationAccountHolder(destination.getAccountHolder());
        payout.setDestinationQrObjectKey(destination.getQrObjectKey());
        payout = payoutRepository.saveAndFlush(payout);

        CreatorEarningEntry earning = new CreatorEarningEntry();
        earning.setCreator(creator);
        earning.setSettlement(settlement);
        earning.setPayout(payout);
        earning.setAmount(80);
        earning.setState(CreatorEarningState.RESERVED);
        earningEntryRepository.saveAndFlush(earning);

        PlatformRevenueEntry revenue = new PlatformRevenueEntry();
        revenue.setSettlement(settlement);
        revenue.setSale(sale);
        revenue.setAmount(20);
        platformRevenueEntryRepository.saveAndFlush(revenue);

        assertThat(saleRepository.findByBuyerUserIdAndIdempotencyKey(buyer.getUserId(), "checkout-1"))
                .map(MarketplaceSale::getSaleId)
                .contains(sale.getSaleId());
        assertThat(entitlementRepository.findByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyer.getUserId(), version.getVersionId(), MarketplaceEntitlementStatus.ACTIVE))
                .map(MarketplaceEntitlement::getEntitlementId)
                .contains(entitlement.getEntitlementId());
        assertThat(settlementRepository.findBySaleSaleId(sale.getSaleId()))
                .map(MarketplaceSaleSettlement::getSettlementId)
                .contains(settlement.getSettlementId());
        assertThat(earningEntryRepository.findByCreatorUserIdAndStateOrderByCreatedAtDesc(
                creator.getUserId(), CreatorEarningState.RESERVED))
                .extracting(CreatorEarningEntry::getAmount)
                .containsExactly(80);
        assertThat(platformRevenueEntryRepository.findBySaleSaleId(sale.getSaleId()))
                .map(PlatformRevenueEntry::getAmount)
                .contains(20);
        assertThat(payoutDestinationRepository.findByCreatorUserIdAndActiveTrue(creator.getUserId()))
                .map(CreatorPayoutDestination::getDestinationId)
                .contains(destination.getDestinationId());
        assertThat(payoutRepository.findByCreatorUserIdOrderByCreatedAtDesc(creator.getUserId()))
                .extracting(CreatorPayout::getPayoutId)
                .containsExactly(payout.getPayoutId());

        var receipt = new MarketplaceCheckoutMapper().toResponse(sale, entitlement, settlement, 900);
        assertThat(receipt.getSaleId()).isEqualTo(sale.getSaleId());
        assertThat(receipt.getEntitlementId()).isEqualTo(entitlement.getEntitlementId());
        assertThat(receipt.getPackVersionId()).isEqualTo(version.getVersionId());
        assertThat(receipt.getCreatorAmount()).isEqualTo(80);
        assertThat(receipt.getPlatformAmount()).isEqualTo(20);
        assertThat(receipt.getRemainingCoinBalance()).isEqualTo(900);
    }

    @Test
    void preventsReusingAnIdempotencyKeyForTheSameBuyer() {
        saleRepository.saveAndFlush(sale("checkout-duplicate"));

        assertThatThrownBy(() -> saleRepository.saveAndFlush(sale("checkout-duplicate")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void recognizedPlatformRevenueCountsOnlyRecordedSettlements() {
        // A recorded sale (20) recognized; a refunded sale whose settlement is REVERSED (30) excluded.
        recordRevenue("recorded-sale", MarketplaceSettlementStatus.RECORDED, 20);
        recordRevenue("reversed-sale", MarketplaceSettlementStatus.REVERSED, 30);

        assertThat(platformRevenueEntryRepository.sumRecognizedPlatformRevenueByVersion(version.getVersionId()))
                .isEqualTo(20L);
    }

    private void recordRevenue(String idempotencyKey, MarketplaceSettlementStatus status, int platformAmount) {
        MarketplaceSale sale = saleRepository.saveAndFlush(sale(idempotencyKey));

        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setSale(sale);
        settlement.setCreator(creator);
        settlement.setCreatorShareBps(8000);
        settlement.setCreatorAmount(100 - platformAmount);
        settlement.setPlatformShareBps(2000);
        settlement.setPlatformAmount(platformAmount);
        settlement.setCoinToVndRate(new BigDecimal("1.0000"));
        settlement.setStatus(status);
        settlement = settlementRepository.saveAndFlush(settlement);

        PlatformRevenueEntry revenue = new PlatformRevenueEntry();
        revenue.setSettlement(settlement);
        revenue.setSale(sale);
        revenue.setAmount(platformAmount);
        platformRevenueEntryRepository.saveAndFlush(revenue);
    }

    private MarketplaceSale sale(String idempotencyKey) {
        MarketplaceSale sale = new MarketplaceSale();
        sale.setBuyer(buyer);
        sale.setPack(pack);
        sale.setPackVersion(version);
        sale.setGrossCoinAmount(100);
        sale.setOriginalGrossCoinAmount(100);
        sale.setDiscountCoinAmount(0);
        sale.setGrossVndAmount(100L);
        sale.setCoinToVndRate(new BigDecimal("1.0000"));
        sale.setStatus(MarketplaceSaleStatus.COMPLETED);
        sale.setIdempotencyKey(idempotencyKey);
        return sale;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(user);
        workspace.setName("Checkout workspace");
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private User user(String userId, String email, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        return user;
    }
}
