package com.skillsprint.service.marketplace;

import com.skillsprint.dto.response.marketplace.MarketplacePurchaseResponse;
import com.skillsprint.entity.*;
import com.skillsprint.enums.marketplace.*;
import com.skillsprint.exception.*;
import com.skillsprint.repository.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePurchaseService {
    MarketplaceItemRepository itemRepository;
    MarketplacePurchaseRepository purchaseRepository;
    UserRepository userRepository;
    UserWalletRepository walletRepository;
    WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public MarketplacePurchaseResponse purchaseWithCoins(String userId, UUID itemId) {
        MarketplaceItem item = itemRepository.findById(itemId).filter(value -> value.getStatus() == MarketplaceItemStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        if (item.getCreator().getUserId().equals(userId)) throw new AppException(ErrorCode.MARKETPLACE_CREATOR_CANNOT_PURCHASE);
        if (purchaseRepository.existsByUserUserIdAndItemItemId(userId, itemId)) throw new AppException(ErrorCode.MARKETPLACE_ALREADY_PURCHASED);
        User buyer = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            UserWallet created = new UserWallet(); created.setUser(buyer); return walletRepository.saveAndFlush(created);
        });
        int price = item.getPriceCoins();
        if (wallet.getBalance() < price) throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        int before = wallet.getBalance(); wallet.setBalance(before - price); walletRepository.save(wallet);
        MarketplacePurchase purchase = new MarketplacePurchase();
        purchase.setUser(buyer); purchase.setItem(item); purchase.setPriceCoins(price); purchase.setPaymentMethod(MarketplacePaymentMethod.COIN);
        purchase.setStatus(MarketplacePurchaseStatus.ACTIVE); purchase.setPurchasedAt(Instant.now()); purchase = purchaseRepository.save(purchase);
        if (price > 0) {
            WalletTransaction transaction = new WalletTransaction(); transaction.setWallet(wallet); transaction.setDirection(WalletTransactionDirection.DEBIT);
            transaction.setAmount(price); transaction.setBalanceBefore(before); transaction.setBalanceAfter(wallet.getBalance());
            transaction.setReferenceType(WalletTransactionReferenceType.MARKETPLACE_PURCHASE); transaction.setReferenceId(purchase.getPurchaseId());
            walletTransactionRepository.save(transaction);
        }
        return MarketplacePurchaseResponse.builder().purchaseId(purchase.getPurchaseId()).itemId(itemId).priceCoins(price)
                .remainingCoins(wallet.getBalance()).purchasedAt(purchase.getPurchasedAt()).build();
    }
}
