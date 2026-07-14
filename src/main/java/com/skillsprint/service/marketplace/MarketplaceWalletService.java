package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.AdjustWalletRequest;
import com.skillsprint.dto.response.marketplace.WalletBalanceResponse;
import com.skillsprint.dto.response.marketplace.WalletTransactionResponse;
import com.skillsprint.entity.*;
import com.skillsprint.enums.marketplace.*;
import com.skillsprint.exception.*;
import com.skillsprint.repository.*;
import java.util.UUID;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceWalletService {
    UserRepository userRepository; UserWalletRepository walletRepository; WalletTransactionRepository transactionRepository;
    @Transactional
    public WalletBalanceResponse adjust(String userId, AdjustWalletRequest request) {
        if (request.getAmount() == 0) throw new AppException(ErrorCode.VALIDATION_ERROR, "amount phải khác 0");
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> { UserWallet value = new UserWallet(); value.setUser(user); return walletRepository.saveAndFlush(value); });
        int before = wallet.getBalance(); int after = before + request.getAmount();
        if (after < 0) throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        wallet.setBalance(after); walletRepository.save(wallet);
        WalletTransaction transaction = new WalletTransaction(); transaction.setWallet(wallet);
        transaction.setDirection(request.getAmount() > 0 ? WalletTransactionDirection.CREDIT : WalletTransactionDirection.DEBIT);
        transaction.setAmount(Math.abs(request.getAmount())); transaction.setBalanceBefore(before); transaction.setBalanceAfter(after);
        transaction.setReferenceType(WalletTransactionReferenceType.ADMIN_ADJUSTMENT); transaction.setReferenceId(UUID.randomUUID());
        transactionRepository.save(transaction);
        return WalletBalanceResponse.builder().userId(userId).balance(after).build();
    }
    @Transactional(readOnly=true)
    public WalletBalanceResponse getBalance(String userId) { return walletRepository.findByUserIdForUpdate(userId).map(w -> WalletBalanceResponse.builder().userId(userId).balance(w.getBalance()).build()).orElse(WalletBalanceResponse.builder().userId(userId).balance(0).build()); }
    @Transactional(readOnly=true)
    public List<WalletTransactionResponse> getTransactions(String userId) { return transactionRepository.findByWalletUserUserIdOrderByCreatedAtDesc(userId).stream().map(t -> WalletTransactionResponse.builder().direction(t.getDirection()).amount(t.getAmount()).balanceAfter(t.getBalanceAfter()).referenceType(t.getReferenceType()).createdAt(t.getCreatedAt()).build()).toList(); }
}
