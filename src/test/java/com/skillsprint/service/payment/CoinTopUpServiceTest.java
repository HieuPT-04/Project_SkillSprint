package com.skillsprint.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.configuration.payment.CoinTopUpProperties;
import com.skillsprint.configuration.payment.CoinTopUpProperties.CoinPackage;
import com.skillsprint.configuration.payment.SepayProperties;
import com.skillsprint.dto.request.marketplace.CreateCoinTopUpRequest;
import com.skillsprint.dto.response.marketplace.CoinPackageResponse;
import com.skillsprint.dto.response.marketplace.CoinTopUpPaymentResponse;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.marketplace.WalletTransactionDirection;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoinTopUpServiceTest {

    static final CoinPackage COIN_100 = new CoinPackage("COIN_100", 100, new BigDecimal("19000"));
    static final CoinPackage COIN_500 = new CoinPackage("COIN_500", 500, new BigDecimal("85000"));

    @Mock UserRepository userRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock UserWalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;

    SepayProperties sepayProperties;
    CoinTopUpService service;
    User user;

    @BeforeEach
    void setUp() {
        sepayProperties = new SepayProperties(
                true,
                "MBBANK",
                "123 456 789",
                "SKILL SPRINT",
                "webhook-secret",
                "https://qr.example/{bankCode}/{accountNumber}?amount={amount}&content={content}",
                20
        );
        service = service(sepayProperties, true);
        user = new User();
        user.setUserId("user-1");
    }

    @Test
    void createdTopUpUsesServerConfiguredVndAndCoinAmounts() {
        stubUserAndSave();

        CoinTopUpPaymentResponse response = service.createTopUpPayment("user-1", request("COIN_500"));

        assertThat(response.getPurpose()).isEqualTo(PaymentPurpose.COIN_TOP_UP);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getPackageKey()).isEqualTo("COIN_500");
        assertThat(response.getCoinAmount()).isEqualTo(500);
        assertThat(response.getAmount()).isEqualByComparingTo("85000");
        assertThat(response.getCurrency()).isEqualTo("VND");
        assertThat(response.getPaymentCode()).startsWith("DH");
        assertThat(response.getQrUrl()).contains("MBBANK").contains("amount=85000");
        assertThat(response.getBank().getAccountNumber()).isEqualTo("123 456 789");
        assertThat(response.getExpiredAt()).isNotNull();
    }

    @Test
    void createdTopUpIsPersistedAsACoinTopUpPaymentWithNoServicePlan() {
        stubUserAndSave();

        service.createTopUpPayment("user-1", request("COIN_100"));

        PaymentTransaction saved = savedPayment();
        assertThat(saved.getPurpose()).isEqualTo(PaymentPurpose.COIN_TOP_UP);
        assertThat(saved.getPlan()).isNull();
        assertThat(saved.getSubscriptionMonths()).isZero();
        assertThat(saved.getCoinAmount()).isEqualTo(100);
        assertThat(saved.getCoinPackageKey()).isEqualTo("COIN_100");
        assertThat(saved.getAmount()).isEqualByComparingTo("19000");
        assertThat(saved.getProvider()).isEqualTo(PaymentProvider.SEPAY);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getTransferContent()).isEqualTo(saved.getTxnRef());
    }

    @Test
    void buyerIsTakenFromTheAuthenticatedCallerNotTheRequest() {
        stubUserAndSave();

        // The request DTO has no user field at all, and the request body a client could
        // send is irrelevant: the caller id is the only identity the service uses.
        service.createTopUpPayment("user-1", request("COIN_100"));

        assertThat(savedPayment().getUser()).isSameAs(user);
        verify(userRepository).findById("user-1");
        verify(userRepository, never()).findById("attacker");
    }

    @Test
    void requestCannotNameAnUnknownPackage() {
        assertThatThrownBy(() -> service.createTopUpPayment("user-1", request("COIN_999999")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COIN_PACKAGE_NOT_FOUND);

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void topUpIsRejectedWhenSepayIsNotConfigured() {
        CoinTopUpService unconfigured = service(
                new SepayProperties(false, "MBBANK", "123456789", "SKILL SPRINT", "secret", "https://qr", 20),
                true
        );

        assertThatThrownBy(() -> unconfigured.createTopUpPayment("user-1", request("COIN_100")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR);

        verify(paymentTransactionRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void topUpIsRejectedWhenCoinTopUpIsDisabled() {
        CoinTopUpService disabled = service(sepayProperties, false);

        assertThatThrownBy(() -> disabled.createTopUpPayment("user-1", request("COIN_100")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.COIN_TOP_UP_NOT_AVAILABLE);

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void unknownUserCannotCreateATopUp() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTopUpPayment("ghost", request("COIN_100")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_PROFILE_NOT_FOUND);

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void availablePackagesExposeServerPricing() {
        List<CoinPackageResponse> packages = service.getAvailablePackages();

        assertThat(packages).extracting(CoinPackageResponse::getPackageKey)
                .containsExactly("COIN_100", "COIN_500");
        assertThat(packages.get(1).getVndAmount()).isEqualByComparingTo("85000");
        assertThat(packages.get(1).getCoinAmount()).isEqualTo(500);
        assertThat(packages.get(0).getCurrency()).isEqualTo("VND");
    }

    @Test
    void buyerCanCancelTheirPendingTopUp() {
        PaymentTransaction payment = pendingTopUp();
        when(paymentTransactionRepository.findWithLockByPaymentId(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.save(payment)).thenReturn(payment);

        CoinTopUpPaymentResponse response = service.cancelPendingTopUp("user-1", payment.getPaymentId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(paymentTransactionRepository).save(payment);
    }

    @Test
    void cancelledTopUpCanBeCancelledAgainWithoutChangingItsState() {
        PaymentTransaction payment = pendingTopUp();
        payment.setStatus(PaymentStatus.CANCELED);
        when(paymentTransactionRepository.findWithLockByPaymentId(payment.getPaymentId())).thenReturn(Optional.of(payment));

        CoinTopUpPaymentResponse response = service.cancelPendingTopUp("user-1", payment.getPaymentId());

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void buyerCannotCancelAnotherUsersTopUp() {
        PaymentTransaction payment = pendingTopUp();
        User otherUser = new User();
        otherUser.setUserId("other-user");
        payment.setUser(otherUser);
        when(paymentTransactionRepository.findWithLockByPaymentId(payment.getPaymentId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.cancelPendingTopUp("user-1", payment.getPaymentId()))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TRANSACTION_NOT_FOUND);

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void paidTopUpCannotBeCancelled() {
        PaymentTransaction payment = paidTopUp(100);
        when(paymentTransactionRepository.findWithLockByPaymentId(payment.getPaymentId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.cancelPendingTopUp("user-1", payment.getPaymentId()))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_CANCELABLE);

        verify(paymentTransactionRepository, never()).save(any());
    }

    // --- crediting a verified top-up ---------------------------------------------

    @Test
    void verifiedTopUpCreditsExactlyTheConfiguredCoinAmountOnce() {
        PaymentTransaction payment = paidTopUp(100);
        UserWallet wallet = wallet(40);
        when(walletTransactionRepository.existsByReferenceTypeAndReferenceId(
                WalletTransactionReferenceType.COIN_TOP_UP, payment.getPaymentId())).thenReturn(false);
        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));

        service.creditVerifiedTopUp(payment);

        assertThat(wallet.getBalance()).isEqualTo(140);
        WalletTransaction ledgerEntry = savedLedgerEntry();
        assertThat(ledgerEntry.getDirection()).isEqualTo(WalletTransactionDirection.CREDIT);
        assertThat(ledgerEntry.getAmount()).isEqualTo(100);
        assertThat(ledgerEntry.getBalanceBefore()).isEqualTo(40);
        assertThat(ledgerEntry.getBalanceAfter()).isEqualTo(140);
        assertThat(ledgerEntry.getReferenceType()).isEqualTo(WalletTransactionReferenceType.COIN_TOP_UP);
        assertThat(ledgerEntry.getReferenceId()).isEqualTo(payment.getPaymentId());
        assertThat(ledgerEntry.getWallet()).isSameAs(wallet);
    }

    @Test
    void balanceEqualsTheSumOfLedgerCreditsAcrossSequentialTopUps() {
        UserWallet wallet = wallet(0);
        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.existsByReferenceTypeAndReferenceId(any(), any())).thenReturn(false);

        service.creditVerifiedTopUp(paidTopUp(100));
        service.creditVerifiedTopUp(paidTopUp(500));
        service.creditVerifiedTopUp(paidTopUp(50));

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        int ledgerSum = captor.getAllValues().stream()
                .mapToInt(entry -> entry.getDirection() == WalletTransactionDirection.CREDIT
                        ? entry.getAmount()
                        : -entry.getAmount())
                .sum();

        assertThat(wallet.getBalance()).isEqualTo(650).isEqualTo(ledgerSum);
        // Each entry picks up exactly where the previous one left off.
        assertThat(captor.getAllValues()).extracting(WalletTransaction::getBalanceAfter)
                .containsExactly(100, 600, 650);
    }

    @Test
    void alreadyCreditedTopUpIsNotCreditedTwice() {
        PaymentTransaction payment = paidTopUp(100);
        when(walletTransactionRepository.existsByReferenceTypeAndReferenceId(
                WalletTransactionReferenceType.COIN_TOP_UP, payment.getPaymentId())).thenReturn(true);

        service.creditVerifiedTopUp(payment);

        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void subscriptionPaymentCanNeverCreditCoin() {
        PaymentTransaction subscription = paidTopUp(100);
        subscription.setPurpose(PaymentPurpose.SUBSCRIPTION);

        assertThatThrownBy(() -> service.creditVerifiedTopUp(subscription))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PURPOSE_MISMATCH);

        verify(walletRepository, never()).save(any());
        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void unpaidTopUpCannotCreditCoin() {
        PaymentTransaction pending = paidTopUp(100);
        pending.setStatus(PaymentStatus.PENDING);

        assertThatThrownBy(() -> service.creditVerifiedTopUp(pending))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PURPOSE_MISMATCH);

        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void topUpWithoutACoinAmountCannotCreditCoin() {
        PaymentTransaction broken = paidTopUp(100);
        broken.setCoinAmount(null);

        assertThatThrownBy(() -> service.creditVerifiedTopUp(broken))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR);

        verify(walletTransactionRepository, never()).save(any());
    }

    @Test
    void firstTopUpCreatesTheBuyerWalletBeforeCrediting() {
        PaymentTransaction payment = paidTopUp(100);
        when(walletTransactionRepository.existsByReferenceTypeAndReferenceId(any(), any())).thenReturn(false);
        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.empty());
        when(walletRepository.saveAndFlush(any(UserWallet.class))).thenAnswer(invocation -> {
            UserWallet created = invocation.getArgument(0);
            created.setWalletId(UUID.randomUUID());
            return created;
        });

        service.creditVerifiedTopUp(payment);

        WalletTransaction ledgerEntry = savedLedgerEntry();
        assertThat(ledgerEntry.getBalanceBefore()).isZero();
        assertThat(ledgerEntry.getBalanceAfter()).isEqualTo(100);
        assertThat(ledgerEntry.getWallet().getUser()).isSameAs(user);
    }

    private PaymentTransaction paidTopUp(int coinAmount) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setPaymentId(UUID.randomUUID());
        payment.setUser(user);
        payment.setPurpose(PaymentPurpose.COIN_TOP_UP);
        payment.setStatus(PaymentStatus.PAID);
        payment.setCoinAmount(coinAmount);
        payment.setCoinPackageKey("COIN_" + coinAmount);
        return payment;
    }

    private PaymentTransaction pendingTopUp() {
        PaymentTransaction payment = paidTopUp(100);
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private UserWallet wallet(int balance) {
        UserWallet wallet = new UserWallet();
        wallet.setWalletId(UUID.randomUUID());
        wallet.setUser(user);
        wallet.setBalance(balance);
        return wallet;
    }

    private WalletTransaction savedLedgerEntry() {
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private void stubUserAndSave() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getPaymentId() == null) {
                transaction.setPaymentId(UUID.randomUUID());
            }
            return transaction;
        });
    }

    private PaymentTransaction savedPayment() {
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private CreateCoinTopUpRequest request(String packageKey) {
        CreateCoinTopUpRequest request = new CreateCoinTopUpRequest();
        request.setPackageKey(packageKey);
        return request;
    }

    private CoinTopUpService service(SepayProperties properties, boolean coinEnabled) {
        return new CoinTopUpService(
                new CoinPackageCatalog(new CoinTopUpProperties(coinEnabled, List.of(COIN_100, COIN_500))),
                new SepayPaymentFactory(properties),
                userRepository,
                paymentTransactionRepository,
                walletRepository,
                walletTransactionRepository
        );
    }
}
