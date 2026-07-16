package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.PurchaseMarketplacePackVersionRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionPurchaseResponse;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.PlatformRevenueEntry;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplaceSaleStatus;
import com.skillsprint.enums.marketplace.MarketplaceSettlementStatus;
import com.skillsprint.enums.marketplace.WalletTransactionDirection;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.MarketplaceCheckoutMapper;
import com.skillsprint.repository.CreatorEarningEntryRepository;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceSaleRepository;
import com.skillsprint.repository.MarketplaceSaleSettlementRepository;
import com.skillsprint.repository.PlatformRevenueEntryRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceVersionCheckoutService {

    static final int CREATOR_SHARE_BPS = 8_000;
    static final int PLATFORM_SHARE_BPS = 2_000;
    static final int INITIAL_UPGRADE_DISCOUNT_COIN = 0;
    static final BigDecimal COIN_TO_VND_RATE = BigDecimal.ONE.setScale(4);

    MarketplacePackVersionRepository versionRepository;
    MarketplaceSaleRepository saleRepository;
    MarketplaceEntitlementRepository entitlementRepository;
    MarketplaceSaleSettlementRepository settlementRepository;
    CreatorEarningEntryRepository earningEntryRepository;
    PlatformRevenueEntryRepository platformRevenueEntryRepository;
    UserRepository userRepository;
    UserWalletRepository walletRepository;
    WalletTransactionRepository walletTransactionRepository;
    MarketplaceCheckoutMapper checkoutMapper;

    @Transactional
    public MarketplaceVersionPurchaseResponse purchaseWithCoins(
            String buyerId,
            UUID versionId,
            PurchaseMarketplacePackVersionRequest request
    ) {
        return checkout(buyerId, versionId, request, false);
    }

    @Transactional
    public MarketplaceVersionPurchaseResponse upgradeWithCoins(
            String buyerId,
            UUID versionId,
            PurchaseMarketplacePackVersionRequest request
    ) {
        return checkout(buyerId, versionId, request, true);
    }

    private MarketplaceVersionPurchaseResponse checkout(
            String buyerId,
            UUID versionId,
            PurchaseMarketplacePackVersionRequest request,
            boolean upgrade
    ) {
        MarketplaceSale existingSale = saleRepository
                .findByBuyerUserIdAndIdempotencyKey(buyerId, request.getIdempotencyKey())
                .orElse(null);
        if (existingSale != null) {
            return replay(existingSale, versionId, buyerId, upgrade);
        }

        MarketplacePackVersion version = versionRepository.findByVersionIdForUpdate(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        if (!version.isSaleable()) {
            throw new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_SALEABLE);
        }
        if (version.getPack().getCreator().getUserId().equals(buyerId)) {
            throw new AppException(ErrorCode.MARKETPLACE_CREATOR_CANNOT_PURCHASE);
        }

        MarketplaceEntitlement sourceEntitlement = upgrade
                ? findUpgradeSourceEntitlement(buyerId, version)
                : null;

        // The version lock serializes first-time buyers of this version. Rechecking after
        // acquiring it closes the race between two requests sharing an idempotency key.
        existingSale = saleRepository.findByBuyerUserIdAndIdempotencyKey(buyerId, request.getIdempotencyKey())
                .orElse(null);
        if (existingSale != null) {
            return replay(existingSale, versionId, buyerId, upgrade);
        }
        if (entitlementRepository.existsByBuyerUserIdAndPackVersionVersionIdAndStatus(
                buyerId, versionId, MarketplaceEntitlementStatus.ACTIVE)) {
            throw new AppException(ErrorCode.MARKETPLACE_ENTITLEMENT_ALREADY_EXISTS);
        }

        UserWallet wallet = walletRepository.findByUserIdForUpdate(buyerId).orElse(null);
        // The wallet lock serializes checkout requests for the same buyer, including
        // requests targeting different versions with the same idempotency key.
        existingSale = saleRepository.findByBuyerUserIdAndIdempotencyKey(buyerId, request.getIdempotencyKey())
                .orElse(null);
        if (existingSale != null) {
            return replay(existingSale, versionId, buyerId, upgrade);
        }
        int originalPrice = version.getPriceCoins();
        int discount = upgrade ? INITIAL_UPGRADE_DISCOUNT_COIN : 0;
        int price = originalPrice - discount;
        int balanceBefore = wallet == null ? 0 : wallet.getBalance();
        if (balanceBefore < price) {
            throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }

        int balanceAfter = balanceBefore - price;
        if (wallet != null && price > 0) {
            wallet.setBalance(balanceAfter);
            walletRepository.save(wallet);
        }

        MarketplaceSale sale = new MarketplaceSale();
        sale.setBuyer(buyer);
        sale.setPack(version.getPack());
        sale.setPackVersion(version);
        sale.setSourceEntitlement(sourceEntitlement);
        sale.setOriginalGrossCoinAmount(originalPrice);
        sale.setDiscountCoinAmount(discount);
        sale.setGrossCoinAmount(price);
        sale.setGrossVndAmount((long) price);
        sale.setCoinToVndRate(COIN_TO_VND_RATE);
        sale.setStatus(MarketplaceSaleStatus.COMPLETED);
        sale.setIdempotencyKey(request.getIdempotencyKey());
        sale = saleRepository.save(sale);

        MarketplaceEntitlement entitlement = new MarketplaceEntitlement();
        entitlement.setBuyer(buyer);
        entitlement.setPackVersion(version);
        entitlement.setSourceSale(sale);
        entitlement.setStatus(MarketplaceEntitlementStatus.ACTIVE);
        entitlement.setGrantedAt(Instant.now());
        entitlement = entitlementRepository.save(entitlement);

        int creatorAmount = Math.toIntExact((long) price * CREATOR_SHARE_BPS / 10_000);
        int platformAmount = price - creatorAmount;
        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setSale(sale);
        settlement.setCreator(version.getPack().getCreator());
        settlement.setCreatorShareBps(CREATOR_SHARE_BPS);
        settlement.setCreatorAmount(creatorAmount);
        settlement.setPlatformShareBps(PLATFORM_SHARE_BPS);
        settlement.setPlatformAmount(platformAmount);
        settlement.setCoinToVndRate(COIN_TO_VND_RATE);
        settlement.setStatus(MarketplaceSettlementStatus.RECORDED);
        settlement = settlementRepository.save(settlement);

        CreatorEarningEntry earning = new CreatorEarningEntry();
        earning.setCreator(version.getPack().getCreator());
        earning.setSettlement(settlement);
        earning.setAmount(creatorAmount);
        earning.setState(CreatorEarningState.PENDING);
        earningEntryRepository.save(earning);

        PlatformRevenueEntry revenue = new PlatformRevenueEntry();
        revenue.setSettlement(settlement);
        revenue.setSale(sale);
        revenue.setAmount(platformAmount);
        platformRevenueEntryRepository.save(revenue);

        if (wallet != null && price > 0) {
            WalletTransaction transaction = new WalletTransaction();
            transaction.setWallet(wallet);
            transaction.setDirection(WalletTransactionDirection.DEBIT);
            transaction.setAmount(price);
            transaction.setBalanceBefore(balanceBefore);
            transaction.setBalanceAfter(balanceAfter);
            transaction.setReferenceType(WalletTransactionReferenceType.MARKETPLACE_SALE);
            transaction.setReferenceId(sale.getSaleId());
            walletTransactionRepository.save(transaction);
        }

        return checkoutMapper.toResponse(sale, entitlement, settlement, balanceAfter);
    }

    private MarketplaceEntitlement findUpgradeSourceEntitlement(String buyerId, MarketplacePackVersion targetVersion) {
        return entitlementRepository
                .findFirstByBuyerUserIdAndStatusAndPackVersionPackPackIdAndPackVersionVersionNoLessThanOrderByPackVersionVersionNoDesc(
                        buyerId,
                        MarketplaceEntitlementStatus.ACTIVE,
                        targetVersion.getPack().getPackId(),
                        targetVersion.getVersionNo())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_UPGRADE_SOURCE_ENTITLEMENT_NOT_FOUND));
    }

    private MarketplaceVersionPurchaseResponse replay(
            MarketplaceSale sale,
            UUID requestedVersionId,
            String buyerId,
            boolean upgrade
    ) {
        if (!sale.getPackVersion().getVersionId().equals(requestedVersionId)) {
            throw new AppException(ErrorCode.MARKETPLACE_CHECKOUT_IDEMPOTENCY_CONFLICT);
        }
        if ((sale.getSourceEntitlement() != null) != upgrade) {
            throw new AppException(ErrorCode.MARKETPLACE_CHECKOUT_IDEMPOTENCY_CONFLICT);
        }

        MarketplaceEntitlement entitlement = entitlementRepository.findBySourceSaleSaleId(sale.getSaleId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_CHECKOUT_IDEMPOTENCY_CONFLICT));
        MarketplaceSaleSettlement settlement = settlementRepository.findBySaleSaleId(sale.getSaleId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_CHECKOUT_IDEMPOTENCY_CONFLICT));
        int balanceAfter = sale.getGrossCoinAmount() == 0
                ? walletRepository.findByUserUserId(buyerId).map(UserWallet::getBalance).orElse(0)
                : walletTransactionRepository.findByReferenceTypeAndReferenceId(
                        WalletTransactionReferenceType.MARKETPLACE_SALE,
                        sale.getSaleId())
                .map(WalletTransaction::getBalanceAfter)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_CHECKOUT_IDEMPOTENCY_CONFLICT));
        return checkoutMapper.toResponse(sale, entitlement, settlement, balanceAfter);
    }
}
