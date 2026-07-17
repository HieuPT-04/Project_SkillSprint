package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.marketplace.PurchaseMarketplacePackVersionRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionPurchaseResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.marketplace.MarketplaceVersionCheckoutService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class MarketplaceVersionCheckoutApiFlowTest {

    private static final String BUYER_ID = "version-checkout-buyer";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    MarketplaceVersionCheckoutService marketplaceVersionCheckoutService;

    @MockBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteById(BUYER_ID);
        userRepository.save(user(BUYER_ID));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(BUYER_ID);
    }

    @Test
    void authenticatedBuyerCanPurchaseVersionWithOwnJwtSubject() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        when(marketplaceVersionCheckoutService.purchaseWithCoins(
                eq(BUYER_ID), eq(versionId), any(PurchaseMarketplacePackVersionRequest.class)))
                .thenReturn(MarketplaceVersionPurchaseResponse.builder()
                        .saleId(saleId)
                        .packVersionId(versionId)
                        .grossCoinAmount(100)
                        .creatorAmount(80)
                        .platformAmount(20)
                        .remainingCoinBalance(400)
                        .build());

        mockMvc.perform(post("/api/marketplace/versions/{versionId}/purchase/coins", versionId)
                        .with(jwtFor(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "checkout-001",
                                  "buyerId": "another-user"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.saleId").value(saleId.toString()))
                .andExpect(jsonPath("$.data.packVersionId").value(versionId.toString()))
                .andExpect(jsonPath("$.data.creatorAmount").value(80))
                .andExpect(jsonPath("$.data.platformAmount").value(20));

        verify(marketplaceVersionCheckoutService).purchaseWithCoins(
                eq(BUYER_ID), eq(versionId), any(PurchaseMarketplacePackVersionRequest.class));
        verify(marketplaceVersionCheckoutService, never()).purchaseWithCoins(
                eq("another-user"), eq(versionId), any(PurchaseMarketplacePackVersionRequest.class));
    }

    @Test
    void checkoutRequiresAuthenticationAndAnIdempotencyKey() throws Exception {
        UUID versionId = UUID.randomUUID();

        mockMvc.perform(post("/api/marketplace/versions/{versionId}/purchase/coins", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"checkout-002\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/marketplace/versions/{versionId}/purchase/coins", versionId)
                        .with(jwtFor(BUYER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(marketplaceVersionCheckoutService, never()).purchaseWithCoins(any(), any(), any());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String userId) {
        return jwt()
                .jwt(token -> token.subject(userId).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Version Checkout Buyer");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
