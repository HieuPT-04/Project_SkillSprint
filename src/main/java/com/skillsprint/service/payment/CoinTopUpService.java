package com.skillsprint.service.payment;

import com.skillsprint.configuration.payment.CoinTopUpProperties.CoinPackage;
import com.skillsprint.dto.request.marketplace.CreateCoinTopUpRequest;
import com.skillsprint.dto.response.marketplace.CoinPackageResponse;
import com.skillsprint.dto.response.marketplace.CoinTopUpPaymentResponse;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.entity.User;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coin top-up lifecycle: create a SePay VND payment for a server-priced Coin package.
 *
 * <p>The buyer is always the authenticated caller passed in by the controller from the
 * JWT subject, and the VND/Coin amounts always come from {@link CoinPackageCatalog}.
 * Nothing about the money or the identity is taken from the request body.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CoinTopUpService {

    /** A top-up buys Coin, never subscription months. */
    static final int NO_SUBSCRIPTION_MONTHS = 0;

    CoinPackageCatalog coinPackageCatalog;
    SepayPaymentFactory sepayPaymentFactory;
    UserRepository userRepository;
    PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(readOnly = true)
    public List<CoinPackageResponse> getAvailablePackages() {
        return coinPackageCatalog.availablePackages().stream()
                .map(coinPackage -> CoinPackageResponse.builder()
                        .packageKey(coinPackage.key())
                        .coinAmount(coinPackage.coinAmount())
                        .vndAmount(coinPackageCatalog.vndAmountOf(coinPackage))
                        .currency("VND")
                        .build())
                .toList();
    }

    @Transactional
    public CoinTopUpPaymentResponse createTopUpPayment(String userId, CreateCoinTopUpRequest request) {
        sepayPaymentFactory.requireReady();

        CoinPackage coinPackage = coinPackageCatalog.require(request.getPackageKey());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        BigDecimal amount = coinPackageCatalog.vndAmountOf(coinPackage);

        PaymentTransaction transaction = sepayPaymentFactory.newPendingPayment(user, amount);
        transaction.setPurpose(PaymentPurpose.COIN_TOP_UP);
        // A top-up never references a service plan; the purpose CHECK constraint in V8
        // rejects the row otherwise.
        transaction.setPlan(null);
        transaction.setSubscriptionMonths(NO_SUBSCRIPTION_MONTHS);
        transaction.setCoinAmount(coinPackage.coinAmount());
        transaction.setCoinPackageKey(coinPackage.key());

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        saved.setQrCodeUrl(sepayPaymentFactory.buildQrCodeUrl(saved));

        return toResponse(paymentTransactionRepository.save(saved));
    }

    private CoinTopUpPaymentResponse toResponse(PaymentTransaction transaction) {
        return CoinTopUpPaymentResponse.builder()
                .paymentId(transaction.getPaymentId())
                .purpose(transaction.getPurpose())
                .status(transaction.getStatus())
                .packageKey(transaction.getCoinPackageKey())
                .coinAmount(transaction.getCoinAmount())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentCode(transaction.getTxnRef())
                .qrUrl(transaction.getQrCodeUrl())
                .bank(CoinTopUpPaymentResponse.BankInfo.builder()
                        .bankCode(transaction.getBankCode())
                        .accountNumber(transaction.getBankAccountNumber())
                        .accountName(transaction.getBankAccountName())
                        .build())
                .expiredAt(transaction.getExpireAt())
                .build();
    }
}
