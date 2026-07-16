package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.marketplace.CreateCoinTopUpRequest;
import com.skillsprint.dto.response.marketplace.CoinPackageResponse;
import com.skillsprint.dto.response.marketplace.CoinTopUpPaymentResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.marketplace.MarketplaceWalletService;
import com.skillsprint.service.payment.CoinTopUpService;
import com.skillsprint.service.ratelimit.RateLimitService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceWalletTopUpApiFlowTest {

    private static final String USER_ID = "topup-flow-user";
    private static final String OTHER_USER_ID = "topup-flow-other";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    CoinTopUpService coinTopUpService;

    @MockBean
    MarketplaceWalletService marketplaceWalletService;

    @MockBean
    RateLimitService rateLimitService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        userRepository.deleteById(USER_ID);
        userRepository.deleteById(OTHER_USER_ID);
        userRepository.save(user(USER_ID, "topup-flow@example.com"));
        userRepository.save(user(OTHER_USER_ID, "topup-flow-other@example.com"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
        userRepository.deleteById(OTHER_USER_ID);
    }

    @Test
    void anonymousUserCannotCreateCoinTopUp() throws Exception {
        mockMvc.perform(post("/api/marketplace/wallet/top-ups/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topUpBody("COIN_100")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(rateLimitService, never()).checkPaymentCreate(any());
        verify(coinTopUpService, never()).createTopUpPayment(any(), any());
    }

    @Test
    void authenticatedUserCanCreateCoinTopUpAndReceivesTransferInstructions() throws Exception {
        when(coinTopUpService.createTopUpPayment(eq(USER_ID), any(CreateCoinTopUpRequest.class)))
                .thenReturn(topUpResponse());

        mockMvc.perform(post("/api/marketplace/wallet/top-ups/sepay")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topUpBody("COIN_100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo lệnh nạp Coin thành công"))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.purpose").value("COIN_TOP_UP"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.packageKey").value("COIN_100"))
                .andExpect(jsonPath("$.data.coinAmount").value(100))
                .andExpect(jsonPath("$.data.amount").value(19000))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.paymentCode").value("DH123"))
                .andExpect(jsonPath("$.data.qrUrl").value("https://qr.example/DH123"))
                .andExpect(jsonPath("$.data.bank.bankCode").value("MBBANK"))
                .andExpect(jsonPath("$.data.bank.accountNumber").value("123456789"))
                .andExpect(jsonPath("$.data.expiredAt").exists());

        verify(rateLimitService).checkPaymentCreate(USER_ID);
    }

    /**
     * The buyer must come from the JWT subject. A body that names another user is
     * ignored: CreateCoinTopUpRequest has no user field, and Jackson drops the unknown
     * property, so the caller can only ever top up their own wallet.
     */
    @Test
    void topUpIgnoresUserIdSuppliedInTheRequestBody() throws Exception {
        when(coinTopUpService.createTopUpPayment(eq(USER_ID), any(CreateCoinTopUpRequest.class)))
                .thenReturn(topUpResponse());

        mockMvc.perform(post("/api/marketplace/wallet/top-ups/sepay")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packageKey": "COIN_100",
                                  "userId": "%s",
                                  "user": "%s",
                                  "coinAmount": 999999,
                                  "vndAmount": 1,
                                  "amount": 1
                                }
                                """.formatted(OTHER_USER_ID, OTHER_USER_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> buyer = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CreateCoinTopUpRequest> body = ArgumentCaptor.forClass(CreateCoinTopUpRequest.class);
        verify(coinTopUpService).createTopUpPayment(buyer.capture(), body.capture());

        org.assertj.core.api.Assertions.assertThat(buyer.getValue()).isEqualTo(USER_ID);
        org.assertj.core.api.Assertions.assertThat(body.getValue().getPackageKey()).isEqualTo("COIN_100");
        verify(coinTopUpService, never()).createTopUpPayment(eq(OTHER_USER_ID), any());
        verify(rateLimitService, never()).checkPaymentCreate(OTHER_USER_ID);
    }

    @Test
    void topUpRequiresAPackageKey() throws Exception {
        mockMvc.perform(post("/api/marketplace/wallet/top-ups/sepay")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(coinTopUpService, never()).createTopUpPayment(any(), any());
    }

    @Test
    void unknownPackageUsesGlobalErrorShape() throws Exception {
        when(coinTopUpService.createTopUpPayment(eq(USER_ID), any(CreateCoinTopUpRequest.class)))
                .thenThrow(new AppException(ErrorCode.COIN_PACKAGE_NOT_FOUND));

        mockMvc.perform(post("/api/marketplace/wallet/top-ups/sepay")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topUpBody("COIN_999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.path").value("/api/marketplace/wallet/top-ups/sepay"));
    }

    @Test
    void topUpCreateIsRateLimitedPerCaller() throws Exception {
        doThrow(new AppException(ErrorCode.RATE_LIMIT_EXCEEDED))
                .when(rateLimitService)
                .checkPaymentCreate(USER_ID);

        mockMvc.perform(post("/api/marketplace/wallet/top-ups/sepay")
                        .with(jwtFor(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topUpBody("COIN_100")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        verify(coinTopUpService, never()).createTopUpPayment(any(), any());
    }

    @Test
    void authenticatedUserCanListServerPricedPackages() throws Exception {
        when(coinTopUpService.getAvailablePackages()).thenReturn(List.of(CoinPackageResponse.builder()
                .packageKey("COIN_100")
                .coinAmount(100)
                .vndAmount(new BigDecimal("19000"))
                .currency("VND")
                .build()));

        mockMvc.perform(get("/api/marketplace/wallet/top-ups/packages").with(jwtFor(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].packageKey").value("COIN_100"))
                .andExpect(jsonPath("$.data[0].coinAmount").value(100))
                .andExpect(jsonPath("$.data[0].vndAmount").value(19000));
    }

    @Test
    void walletReadsAreScopedToTheCallingUser() throws Exception {
        when(marketplaceWalletService.getTransactions(OTHER_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/marketplace/wallet/transactions").with(jwtFor(OTHER_USER_ID)))
                .andExpect(status().isOk());

        verify(marketplaceWalletService).getTransactions(OTHER_USER_ID);
        verify(marketplaceWalletService, never()).getTransactions(USER_ID);
    }

    private CoinTopUpPaymentResponse topUpResponse() {
        return CoinTopUpPaymentResponse.builder()
                .paymentId(paymentId)
                .purpose(PaymentPurpose.COIN_TOP_UP)
                .status(PaymentStatus.PENDING)
                .packageKey("COIN_100")
                .coinAmount(100)
                .amount(new BigDecimal("19000"))
                .currency("VND")
                .paymentCode("DH123")
                .qrUrl("https://qr.example/DH123")
                .bank(CoinTopUpPaymentResponse.BankInfo.builder()
                        .bankCode("MBBANK")
                        .accountNumber("123456789")
                        .accountName("SKILL SPRINT")
                        .build())
                .expiredAt(Instant.parse("2026-06-23T10:20:00Z"))
                .build();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String userId) {
        return jwt()
                .jwt(token -> token.subject(userId).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private String topUpBody(String packageKey) {
        return """
                {
                  "packageKey": "%s"
                }
                """.formatted(packageKey);
    }

    private User user(String userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName("Top Up Flow");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
