package com.skillsprint.flow;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.response.marketplace.MarketplaceQualityJobResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.marketplace.MarketplaceQualityService;
import java.util.List;
import java.util.UUID;
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
class MarketplaceQualityApiFlowTest {

    static final String CREATOR_ID = "quality-flow-creator";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @MockBean MarketplaceQualityService qualityService;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteById(CREATOR_ID);
        User user = new User();
        user.setUserId(CREATOR_ID);
        user.setEmail("quality-flow@example.com");
        user.setFullName("Quality Creator");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(CREATOR_ID);
    }

    @Test
    void creatorQueuesAndReadsOwnLatestQualityJob() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        MarketplaceQualityJobResponse response = MarketplaceQualityJobResponse.builder()
                .jobId(jobId)
                .versionId(versionId)
                .status(MarketplaceQualityJobStatus.QUEUED)
                .currentSnapshot(true)
                .retryCount(0)
                .maxRetries(2)
                .build();
        when(qualityService.queueForCreator(CREATOR_ID, versionId)).thenReturn(response);
        when(qualityService.getLatestForCreator(CREATOR_ID, versionId)).thenReturn(response);

        mockMvc.perform(post("/api/marketplace/creator/versions/{versionId}/quality-jobs", versionId)
                        .with(creatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.currentSnapshot").value(true));

        mockMvc.perform(get("/api/marketplace/creator/versions/{versionId}/quality-jobs/latest", versionId)
                        .with(creatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(versionId.toString()));

        verify(qualityService).queueForCreator(CREATOR_ID, versionId);
        verify(qualityService).getLatestForCreator(CREATOR_ID, versionId);
    }

    @Test
    void qualityEndpointsRequireAuthentication() throws Exception {
        UUID versionId = UUID.randomUUID();

        mockMvc.perform(post("/api/marketplace/creator/versions/{versionId}/quality-jobs", versionId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/marketplace/creator/versions/{versionId}/quality-jobs/latest", versionId))
                .andExpect(status().isUnauthorized());

        verify(qualityService, never()).queueForCreator(CREATOR_ID, versionId);
        verify(qualityService, never()).getLatestForCreator(CREATOR_ID, versionId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor creatorJwt() {
        return jwt()
                .jwt(token -> token.subject(CREATOR_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }
}
