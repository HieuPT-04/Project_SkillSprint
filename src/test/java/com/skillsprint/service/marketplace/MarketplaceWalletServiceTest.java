package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.marketplace.AdjustWalletRequest;
import com.skillsprint.dto.response.marketplace.WalletBalanceResponse;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceWalletServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserWalletRepository walletRepository;
    @Mock WalletTransactionRepository transactionRepository;

    MarketplaceWalletService service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceWalletService(userRepository, walletRepository, transactionRepository);
    }

    @Test
    void getsExistingBalanceWithoutTakingTheWriteLock() {
        UserWallet wallet = new UserWallet();
        wallet.setBalance(120);
        when(walletRepository.findByUserUserId("user-1")).thenReturn(Optional.of(wallet));

        WalletBalanceResponse response = service.getBalance("user-1");

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getBalance()).isEqualTo(120);
        verify(walletRepository).findByUserUserId("user-1");
        verify(walletRepository, never()).findByUserIdForUpdate("user-1");
    }

    @Test
    void returnsZeroForAUserWithoutAWalletWithoutCreatingOne() {
        when(walletRepository.findByUserUserId("user-1")).thenReturn(Optional.empty());

        WalletBalanceResponse response = service.getBalance("user-1");

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getBalance()).isZero();
        verify(walletRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(walletRepository, never()).findByUserIdForUpdate("user-1");
    }

    @Test
    void keepsTheWriteLockForWalletAdjustments() {
        User user = new User();
        user.setUserId("user-1");
        UserWallet wallet = new UserWallet();
        wallet.setBalance(120);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));
        AdjustWalletRequest request = new AdjustWalletRequest();
        request.setAmount(25);

        WalletBalanceResponse response = service.adjust("user-1", request);

        assertThat(response.getBalance()).isEqualTo(145);
        verify(walletRepository).findByUserIdForUpdate("user-1");
        verify(walletRepository, never()).findByUserUserId("user-1");
    }
}
