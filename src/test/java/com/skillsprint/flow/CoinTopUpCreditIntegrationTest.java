package com.skillsprint.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skillsprint.dto.request.admin.ReconcilePaymentRequest;
import com.skillsprint.dto.request.marketplace.CreateCoinTopUpRequest;
import com.skillsprint.dto.request.payment.SepayWebhookRequest;
import com.skillsprint.dto.response.marketplace.CoinTopUpPaymentResponse;
import com.skillsprint.dto.response.marketplace.WalletTransactionResponse;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.entity.User;
import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.marketplace.WalletTransactionDirection;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import com.skillsprint.service.marketplace.MarketplaceWalletService;
import com.skillsprint.service.payment.AdminPaymentService;
import com.skillsprint.service.payment.CoinTopUpService;
import com.skillsprint.service.payment.SepayPaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the real credit paths against real repositories, so the wallet credit and
 * its ledger entry are asserted through actual persistence rather than mocks. A top-up
 * can be confirmed by the SePay webhook or by an admin manual reconciliation, and both
 * must land the same single credit.
 *
 * <p>Deliberately not {@code @Transactional}: each confirmation must commit on its own,
 * the way two separate provider deliveries would.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoinTopUpCreditIntegrationTest {

    private static final String USER_ID = "coin-topup-int-user";
    private static final String OTHER_USER_ID = "coin-topup-int-other";
    private static final String WEBHOOK_KEY = "test-webhook-secret";

    @Autowired SepayPaymentService sepayPaymentService;
    @Autowired AdminPaymentService adminPaymentService;
    @Autowired CoinTopUpService coinTopUpService;
    @Autowired MarketplaceWalletService marketplaceWalletService;
    @Autowired UserRepository userRepository;
    @Autowired UserWalletRepository walletRepository;
    @Autowired WalletTransactionRepository walletTransactionRepository;
    @Autowired PaymentTransactionRepository paymentTransactionRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
        userRepository.save(user(USER_ID, "coin-topup-int@example.com"));
        userRepository.save(user(OTHER_USER_ID, "coin-topup-int-other@example.com"));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void verifiedWebhookCreditsTheExactCoinAmountExactlyOnceForRepeatedDeliveries() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        assertThat(topUp.getCoinAmount()).isEqualTo(100);
        assertThat(topUp.getAmount()).isEqualByComparingTo("100");
        assertThat(balanceOf(USER_ID)).isZero();

        sepayPaymentService.handleWebhook(webhook(9101L, topUp, "100"), null, WEBHOOK_KEY);

        assertThat(balanceOf(USER_ID)).isEqualTo(100);
        assertThat(paymentOf(topUp).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentOf(topUp).getPaidAt()).isNotNull();
        assertThat(paymentOf(topUp).getProviderTransactionId()).isEqualTo("9101");

        // Same delivery again, then a redelivery carrying a different provider id.
        sepayPaymentService.handleWebhook(webhook(9101L, topUp, "100"), null, WEBHOOK_KEY);
        sepayPaymentService.handleWebhook(webhook(9102L, topUp, "100"), null, WEBHOOK_KEY);

        assertThat(balanceOf(USER_ID)).isEqualTo(100);
        assertThat(coinTopUpLedger()).hasSize(1);
        assertThat(coinTopUpLedger().get(0).getReferenceId()).isEqualTo(topUp.getPaymentId());
        assertThat(coinTopUpLedger().get(0).getAmount()).isEqualTo(100);
    }

    /**
     * A Coin top-up has no service plan. Manual reconciliation used to hand that null
     * plan to the subscription activation, so a top-up an admin confirmed by hand never
     * credited any Coin. It must now reach the same end state as the webhook.
     */
    @Test
    void manuallyReconciledTopUpCreditsTheWalletExactlyOnceAndActivatesNoSubscription() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_500");
        assertThat(paymentOf(topUp).getPlan()).isNull();
        assertThat(balanceOf(USER_ID)).isZero();

        adminPaymentService.reconcilePayment(topUp.getPaymentId(), reconcileRequest("BANK-MANUAL-1"));

        assertThat(balanceOf(USER_ID)).isEqualTo(500);
        assertThat(paymentOf(topUp).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentOf(topUp).getProviderTransactionId()).isEqualTo("BANK-MANUAL-1");
        assertThat(paymentOf(topUp).getPlan()).isNull();

        List<WalletTransaction> ledger = coinTopUpLedger();
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getAmount()).isEqualTo(500);
        assertThat(ledger.get(0).getReferenceId()).isEqualTo(topUp.getPaymentId());

        // Reconciling again is refused, so an admin cannot double-credit by retrying.
        assertThatThrownBy(() -> adminPaymentService.reconcilePayment(
                topUp.getPaymentId(), reconcileRequest("BANK-MANUAL-2")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_CONFIRMED);

        assertThat(balanceOf(USER_ID)).isEqualTo(500);
        assertThat(coinTopUpLedger()).hasSize(1);
    }

    /**
     * A top-up already credited by the webhook cannot be credited again by a manual
     * reconciliation, because the payment has already left PENDING.
     */
    @Test
    void manualReconciliationCannotDoubleCreditAWebhookConfirmedTopUp() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        sepayPaymentService.handleWebhook(webhook(9801L, topUp, "100"), null, WEBHOOK_KEY);
        assertThat(balanceOf(USER_ID)).isEqualTo(100);

        assertThatThrownBy(() -> adminPaymentService.reconcilePayment(
                topUp.getPaymentId(), reconcileRequest("BANK-MANUAL-3")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_CONFIRMED);

        assertThat(balanceOf(USER_ID)).isEqualTo(100);
        assertThat(coinTopUpLedger()).hasSize(1);
    }

    /**
     * The webhook path short-circuits on the provider transaction id and on the payment
     * leaving PENDING, so this drives the credit directly to prove the innermost guard:
     * the ledger itself refuses a second credit for the same payment.
     */
    @Test
    void creditingAnAlreadyCreditedPaymentIsANoOp() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        sepayPaymentService.handleWebhook(webhook(9111L, topUp, "100"), null, WEBHOOK_KEY);
        assertThat(balanceOf(USER_ID)).isEqualTo(100);

        coinTopUpService.creditVerifiedTopUp(paymentOf(topUp));
        coinTopUpService.creditVerifiedTopUp(paymentOf(topUp));

        assertThat(balanceOf(USER_ID)).isEqualTo(100);
        assertThat(coinTopUpLedger()).hasSize(1);
    }

    @Test
    void walletBalanceEqualsTheSumOfItsLedgerEntries() {
        creditTopUp("COIN_100", 9201L, "100");
        creditTopUp("COIN_500", 9202L, "500");
        creditTopUp("COIN_50", 9203L, "50");

        List<WalletTransaction> ledger = walletTransactionRepository
                .findByWalletUserUserIdOrderByCreatedAtDesc(USER_ID);
        int ledgerSum = ledger.stream()
                .mapToInt(entry -> entry.getDirection() == WalletTransactionDirection.CREDIT
                        ? entry.getAmount()
                        : -entry.getAmount())
                .sum();

        assertThat(ledger).hasSize(3);
        assertThat(ledgerSum).isEqualTo(650);
        assertThat(balanceOf(USER_ID)).isEqualTo(650);
    }

    @Test
    void transactionHistoryExposesPurposeAndReferenceForTheOwnerOnly() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        sepayPaymentService.handleWebhook(webhook(9301L, topUp, "100"), null, WEBHOOK_KEY);

        List<WalletTransactionResponse> history = marketplaceWalletService.getTransactions(USER_ID);

        assertThat(history).hasSize(1);
        WalletTransactionResponse entry = history.get(0);
        assertThat(entry.getTransactionId()).isNotNull();
        assertThat(entry.getDirection()).isEqualTo(WalletTransactionDirection.CREDIT);
        assertThat(entry.getReferenceType()).isEqualTo(WalletTransactionReferenceType.COIN_TOP_UP);
        assertThat(entry.getReferenceId()).isEqualTo(topUp.getPaymentId());
        assertThat(entry.getAmount()).isEqualTo(100);
        assertThat(entry.getBalanceBefore()).isZero();
        assertThat(entry.getBalanceAfter()).isEqualTo(100);

        // Another user's history and balance are untouched by this top-up.
        assertThat(marketplaceWalletService.getTransactions(OTHER_USER_ID)).isEmpty();
        assertThat(balanceOf(OTHER_USER_ID)).isZero();
    }

    @Test
    void webhookWithTheWrongAmountDoesNotCreditCoin() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");

        assertThatThrownBy(() -> sepayPaymentService.handleWebhook(webhook(9401L, topUp, "1000"), null, WEBHOOK_KEY))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_INVALID_AMOUNT);

        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(coinTopUpLedger()).isEmpty();
        assertThat(paymentOf(topUp).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void webhookFromTheWrongReceiverAccountDoesNotCreditCoin() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        SepayWebhookRequest request = webhook(9501L, topUp, "100");
        request.setAccountNumber("999999999");
        request.setSubAccount(null);

        assertThatThrownBy(() -> sepayPaymentService.handleWebhook(request, null, WEBHOOK_KEY))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_INVALID_RECEIVER_ACCOUNT);

        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(coinTopUpLedger()).isEmpty();
    }

    @Test
    void webhookWithInvalidAuthenticationDoesNotCreditCoin() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");

        assertThatThrownBy(() -> sepayPaymentService.handleWebhook(webhook(9601L, topUp, "100"), null, "wrong-key"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_INVALID_SIGNATURE);

        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(coinTopUpLedger()).isEmpty();
    }

    @Test
    void webhookForAnExpiredTopUpExpiresItWithoutCreditingCoin() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        PaymentTransaction payment = paymentOf(topUp);
        payment.setExpireAt(Instant.now().minusSeconds(60));
        paymentTransactionRepository.saveAndFlush(payment);

        sepayPaymentService.handleWebhook(webhook(9701L, topUp, "100"), null, WEBHOOK_KEY);

        assertThat(paymentOf(topUp).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(coinTopUpLedger()).isEmpty();
    }

    @Test
    void aSubscriptionPaymentCannotBeCreditedAsCoin() {
        CoinTopUpPaymentResponse topUp = createTopUp("COIN_100");
        PaymentTransaction payment = paymentOf(topUp);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPurpose(PaymentPurpose.SUBSCRIPTION);

        assertThatThrownBy(() -> coinTopUpService.creditVerifiedTopUp(payment))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PURPOSE_MISMATCH);

        assertThat(balanceOf(USER_ID)).isZero();
        assertThat(coinTopUpLedger()).isEmpty();
    }

    private void creditTopUp(String packageKey, long providerId, String amount) {
        CoinTopUpPaymentResponse topUp = createTopUp(packageKey);
        sepayPaymentService.handleWebhook(webhook(providerId, topUp, amount), null, WEBHOOK_KEY);
    }

    private ReconcilePaymentRequest reconcileRequest(String providerTransactionId) {
        ReconcilePaymentRequest request = new ReconcilePaymentRequest();
        request.setProviderTransactionId(providerTransactionId);
        request.setNote("Admin checked bank statement");
        return request;
    }

    private CoinTopUpPaymentResponse createTopUp(String packageKey) {
        CreateCoinTopUpRequest request = new CreateCoinTopUpRequest();
        request.setPackageKey(packageKey);
        return coinTopUpService.createTopUpPayment(USER_ID, request);
    }

    private PaymentTransaction paymentOf(CoinTopUpPaymentResponse topUp) {
        return paymentTransactionRepository.findById(topUp.getPaymentId()).orElseThrow();
    }

    private int balanceOf(String userId) {
        return marketplaceWalletService.getBalance(userId).getBalance();
    }

    private List<WalletTransaction> coinTopUpLedger() {
        return walletTransactionRepository.findByWalletUserUserIdOrderByCreatedAtDesc(USER_ID).stream()
                .filter(entry -> entry.getReferenceType() == WalletTransactionReferenceType.COIN_TOP_UP)
                .toList();
    }

    private SepayWebhookRequest webhook(Long providerId, CoinTopUpPaymentResponse topUp, String amount) {
        SepayWebhookRequest request = new SepayWebhookRequest();
        request.setId(providerId);
        request.setAccountNumber("123456789");
        request.setCode(topUp.getPaymentCode());
        request.setContent("Nap coin " + topUp.getPaymentCode());
        request.setDescription("Top up " + topUp.getPaymentCode());
        request.setReferenceCode("BANK-REF-" + providerId);
        request.setTransferType("in");
        request.setTransferAmount(new BigDecimal(amount));
        return request;
    }

    private void cleanUp() {
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        userRepository.deleteById(USER_ID);
        userRepository.deleteById(OTHER_USER_ID);
    }

    private User user(String userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName("Coin Top Up");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
