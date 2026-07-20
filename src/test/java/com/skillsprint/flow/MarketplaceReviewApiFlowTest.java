package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewCollectionResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewContextResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.marketplace.MarketplaceReviewIneligibilityReason;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.marketplace.MarketplaceReviewService;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceReviewApiFlowTest {

    private static final String USER_ID = "marketplace-review-flow-user";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    @MockBean MarketplaceReviewService marketplaceReviewService;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteById(USER_ID);
        userRepository.save(user());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(USER_ID);
    }

    @Test
    void versionReviewEndpointsUseJwtSubjectAndExposeVersionContract() throws Exception {
        UUID packId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MarketplaceReviewResponse review = review(packId, versionId);
        when(marketplaceReviewService.getVersionReviews(USER_ID, versionId))
                .thenReturn(MarketplaceReviewCollectionResponse.builder()
                        .packId(packId)
                        .versionId(versionId)
                        .versionNo(2)
                        .averageRating(4.5D)
                        .reviewCount(2)
                        .reviews(List.of(review))
                        .build());
        when(marketplaceReviewService.getReviewContext(USER_ID, versionId))
                .thenReturn(MarketplaceReviewContextResponse.builder()
                        .packId(packId)
                        .versionId(versionId)
                        .versionNo(2)
                        .eligible(true)
                        .currentUserReview(review)
                        .build());
        when(marketplaceReviewService.upsertVersion(
                eq(USER_ID),
                eq(versionId),
                any(UpsertMarketplaceReviewRequest.class)
        )).thenReturn(review);

        mockMvc.perform(get("/api/marketplace/versions/{versionId}/reviews", versionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.packId").value(packId.toString()))
                .andExpect(jsonPath("$.data.versionId").value(versionId.toString()))
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.averageRating").value(4.5D))
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.reviews[0].reviewerName").value("Review Buyer"))
                .andExpect(jsonPath("$.data.reviews[0].mine").value(true));

        mockMvc.perform(get("/api/marketplace/versions/{versionId}/reviews/me", versionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(true))
                .andExpect(jsonPath("$.data.ineligibilityReason").doesNotExist())
                .andExpect(jsonPath("$.data.currentUserReview.reviewId")
                        .value(review.getReviewId().toString()));

        mockMvc.perform(put("/api/marketplace/versions/{versionId}/reviews/me", versionId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "comment": "Excellent version"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.mine").value(true));

        verify(marketplaceReviewService).getVersionReviews(USER_ID, versionId);
        verify(marketplaceReviewService).getReviewContext(USER_ID, versionId);
        verify(marketplaceReviewService).upsertVersion(
                eq(USER_ID),
                eq(versionId),
                argThat(request -> Integer.valueOf(5).equals(request.getRating())
                        && "Excellent version".equals(request.getComment()))
        );
    }

    @Test
    void reviewContextExposesMachineReadableIneligibilityReason() throws Exception {
        UUID packId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(marketplaceReviewService.getReviewContext(USER_ID, versionId))
                .thenReturn(MarketplaceReviewContextResponse.builder()
                        .packId(packId)
                        .versionId(versionId)
                        .versionNo(1)
                        .eligible(false)
                        .ineligibilityReason(MarketplaceReviewIneligibilityReason.QUIZ_COMPLETION_REQUIRED)
                        .build());

        mockMvc.perform(get("/api/marketplace/versions/{versionId}/reviews/me", versionId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(false))
                .andExpect(jsonPath("$.data.ineligibilityReason")
                        .value("QUIZ_COMPLETION_REQUIRED"))
                .andExpect(jsonPath("$.data.currentUserReview").doesNotExist());
    }

    @Test
    void invalidVersionReviewIsRejectedBeforeServiceCall() throws Exception {
        UUID versionId = UUID.randomUUID();

        mockMvc.perform(put("/api/marketplace/versions/{versionId}/reviews/me", versionId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 0,
                                  "comment": "Invalid rating"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        verify(marketplaceReviewService, never()).upsertVersion(any(), any(), any());
    }

    @Test
    void legacyItemReviewEndpointsRemainCompatible() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MarketplaceReviewResponse review = review(packId, versionId);
        when(marketplaceReviewService.getReviews(USER_ID, itemId)).thenReturn(List.of(review));
        when(marketplaceReviewService.upsert(
                eq(USER_ID),
                eq(itemId),
                any(UpsertMarketplaceReviewRequest.class)
        )).thenReturn(review);

        mockMvc.perform(get("/api/marketplace/items/{itemId}/reviews", itemId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userName").value("Review Buyer"))
                .andExpect(jsonPath("$.data[0].reviewerName").value("Review Buyer"))
                .andExpect(jsonPath("$.data[0].versionId").value(versionId.toString()))
                .andExpect(jsonPath("$.data[0].mine").value(true));

        mockMvc.perform(post("/api/marketplace/items/{itemId}/review", itemId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Compatible\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewId").value(review.getReviewId().toString()));

        verify(marketplaceReviewService).getReviews(USER_ID, itemId);
        verify(marketplaceReviewService).upsert(
                eq(USER_ID),
                eq(itemId),
                any(UpsertMarketplaceReviewRequest.class)
        );
    }

    @Test
    void allReviewEndpointsRequireAuthentication() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String validBody = "{\"rating\":5,\"comment\":\"Review\"}";

        mockMvc.perform(get("/api/marketplace/versions/{versionId}/reviews", versionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/marketplace/versions/{versionId}/reviews/me", versionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/marketplace/versions/{versionId}/reviews/me", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/marketplace/items/{itemId}/reviews", itemId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/marketplace/items/{itemId}/review", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(marketplaceReviewService);
    }

    private MarketplaceReviewResponse review(UUID packId, UUID versionId) {
        return MarketplaceReviewResponse.builder()
                .reviewId(UUID.randomUUID())
                .packId(packId)
                .versionId(versionId)
                .versionNo(2)
                .userName("Review Buyer")
                .reviewerName("Review Buyer")
                .rating(5)
                .comment("Excellent version")
                .createdAt(Instant.parse("2026-07-19T10:00:00Z"))
                .updatedAt(Instant.parse("2026-07-19T10:05:00Z"))
                .mine(true)
                .build();
    }

    private RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(token -> token.subject(USER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private User user() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail(USER_ID + "@example.com");
        user.setFullName("Review Buyer");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
