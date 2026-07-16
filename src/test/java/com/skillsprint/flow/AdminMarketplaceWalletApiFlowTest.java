package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.marketplace.AdjustWalletRequest;
import com.skillsprint.dto.response.marketplace.AdminWalletResponse;
import com.skillsprint.dto.response.marketplace.WalletBalanceResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.marketplace.MarketplaceWalletService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMarketplaceWalletApiFlowTest {

    private static final String ADMIN_ID = "admin-1";
    private static final String LEARNER_ID = "learner-1";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @MockBean MarketplaceWalletService marketplaceWalletService;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
        userRepository.save(user(ADMIN_ID, "admin-wallet-flow@example.com", "Wallet Admin"));
        userRepository.save(user(LEARNER_ID, "learner-wallet-flow@example.com", "Wallet Learner"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(LEARNER_ID);
    }

    @Test
    void adminCanReadAnOwnedWalletAudit() throws Exception {
        when(marketplaceWalletService.getAdminWallet(LEARNER_ID)).thenReturn(AdminWalletResponse.builder()
                .userId(LEARNER_ID)
                .balance(120)
                .recentTransactions(List.of())
                .build());

        mockMvc.perform(get("/api/admin/wallet/{userId}", LEARNER_ID).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(LEARNER_ID))
                .andExpect(jsonPath("$.data.balance").value(120));

        verify(marketplaceWalletService).getAdminWallet(LEARNER_ID);
    }

    @Test
    void adminAdjustmentUsesTheJwtActorAndRequiresAReason() throws Exception {
        when(marketplaceWalletService.adjust(eq(ADMIN_ID), eq(LEARNER_ID), any(AdjustWalletRequest.class)))
                .thenReturn(WalletBalanceResponse.builder().userId(LEARNER_ID).balance(150).build());

        mockMvc.perform(post("/api/admin/wallet/{userId}/adjust", LEARNER_ID)
                        .with(adminJwt())
                        .contentType("application/json")
                        .content("{\"amount\":30,\"reason\":\"Khuyến mãi hỗ trợ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(150));

        verify(marketplaceWalletService).adjust(eq(ADMIN_ID), eq(LEARNER_ID), any(AdjustWalletRequest.class));

        mockMvc.perform(post("/api/admin/wallet/{userId}/adjust", LEARNER_ID)
                        .with(adminJwt())
                        .contentType("application/json")
                        .content("{\"amount\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void learnerCannotReadOrAdjustWallets() throws Exception {
        mockMvc.perform(get("/api/admin/wallet/{userId}", LEARNER_ID).with(learnerJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/wallet/{userId}/adjust", LEARNER_ID)
                        .with(learnerJwt())
                        .contentType("application/json")
                        .content("{\"amount\":30,\"reason\":\"No access\"}"))
                .andExpect(status().isForbidden());

        verify(marketplaceWalletService, never()).getAdminWallet(any());
        verify(marketplaceWalletService, never()).adjust(any(), any(), any());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject(ADMIN_ID).claim("cognito:groups", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt().jwt(token -> token.subject(LEARNER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user(String userId, String email, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
