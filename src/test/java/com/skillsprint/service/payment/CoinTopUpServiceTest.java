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
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.UserRepository;
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
                paymentTransactionRepository
        );
    }
}
