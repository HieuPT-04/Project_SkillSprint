package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.feedback.CreateFeedbackRequest;
import com.skillsprint.dto.request.feedback.CreateFeedbackUploadUrlRequest;
import com.skillsprint.dto.request.feedback.ReplyFeedbackRequest;
import com.skillsprint.dto.request.feedback.UpdateFeedbackStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.feedback.FeedbackAdminResponse;
import com.skillsprint.dto.response.feedback.FeedbackResponse;
import com.skillsprint.dto.response.feedback.FeedbackSubmitResponse;
import com.skillsprint.dto.response.feedback.FeedbackUploadUrlResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.feedback.FeedbackStatus;
import com.skillsprint.enums.feedback.FeedbackType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.feedback.FeedbackService;
import com.skillsprint.service.storage.S3PresignedUrlService;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackApiFlowTest {

    private static final String LEARNER_ID = "feedback-flow-learner";
    private static final String ADMIN_ID = "feedback-flow-admin";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    FeedbackService feedbackService;

    @MockBean
    S3PresignedUrlService s3PresignedUrlService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID feedbackId;

    @BeforeEach
    void setUp() {
        feedbackId = UUID.randomUUID();
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(ADMIN_ID);
        userRepository.save(user(LEARNER_ID, "feedback-learner@example.com", "Feedback Learner"));
        userRepository.save(user(ADMIN_ID, "feedback-admin@example.com", "Feedback Admin"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(ADMIN_ID);
    }

    @Test
    void anonymousUserCannotUseFeedbackEndpoints() throws Exception {
        mockMvc.perform(get("/api/feedback"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BUG",
                                  "title": "Bug title",
                                  "content": "Bug content"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/admin/feedback"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(feedbackService, never()).getMyFeedback(any());
        verify(feedbackService, never()).createFeedback(any(), any());
        verify(feedbackService, never()).getAdminFeedback(any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void learnerFeedbackEndpointsReturnExpectedShapesAndValidationErrors() throws Exception {
        when(feedbackService.getMyFeedback(LEARNER_ID)).thenReturn(List.of(feedbackResponse()));
        when(feedbackService.getMyFeedbackDetail(LEARNER_ID, feedbackId)).thenReturn(feedbackResponse());
        when(feedbackService.createFeedback(eq(LEARNER_ID), any(CreateFeedbackRequest.class)))
                .thenReturn(feedbackSubmitResponse());
        when(s3PresignedUrlService.createFeedbackImageUploadUrl(
                eq(LEARNER_ID),
                any(CreateFeedbackUploadUrlRequest.class)
        )).thenReturn(uploadUrlResponse());

        mockMvc.perform(get("/api/feedback")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thành công"))
                .andExpect(jsonPath("$.data[0].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));

        mockMvc.perform(get("/api/feedback/{feedbackId}", feedbackId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Login bug"))
                .andExpect(jsonPath("$.data.adminReply").value("We are checking it"));

        mockMvc.perform(post("/api/feedback")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BUG",
                                  "title": "Login bug",
                                  "content": "Login fails sometimes",
                                  "relatedUrl": "https://app.example.com/login",
                                  "imageObjectKey": "feedback/learner/login-bug.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Gửi feedback thành công"))
                .andExpect(jsonPath("$.data.feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        mockMvc.perform(post("/api/feedback/upload-url")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "login-bug.png",
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey").value("feedback/learner/login-bug.png"))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example.com/upload"));

        mockMvc.perform(post("/api/feedback")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": null,
                                  "title": "",
                                  "content": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void learnerFeedbackBusinessErrorsAreMapped() throws Exception {
        when(feedbackService.getMyFeedbackDetail(LEARNER_ID, feedbackId))
                .thenThrow(new AppException(ErrorCode.FEEDBACK_NOT_FOUND));

        mockMvc.perform(get("/api/feedback/{feedbackId}", feedbackId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));

        when(s3PresignedUrlService.createFeedbackImageUploadUrl(
                eq(LEARNER_ID),
                any(CreateFeedbackUploadUrlRequest.class)
        )).thenThrow(new AppException(ErrorCode.INVALID_FEEDBACK_IMAGE_CONTENT_TYPE));

        mockMvc.perform(post("/api/feedback/upload-url")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "bad.txt",
                                  "contentType": "text/plain"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void learnerCannotUseAdminFeedbackEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/feedback")
                        .with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(patch("/api/admin/feedback/{feedbackId}/status", feedbackId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(feedbackService, never()).getAdminFeedback(any(), any(), any(), any(Integer.class), any(Integer.class));
        verify(feedbackService, never()).updateFeedbackStatus(any(), any());
    }

    @Test
    void adminFeedbackEndpointsReturnExpectedShapesAndMapErrors() throws Exception {
        when(feedbackService.getAdminFeedback(FeedbackType.BUG, FeedbackStatus.OPEN, "login", 0, 10))
                .thenReturn(adminPageResponse());
        when(feedbackService.getFeedback(feedbackId)).thenReturn(feedbackAdminResponse());
        when(feedbackService.updateFeedbackStatus(eq(feedbackId), any(UpdateFeedbackStatusRequest.class)))
                .thenReturn(feedbackAdminResponse(FeedbackStatus.IN_PROGRESS));
        when(feedbackService.replyFeedback(eq(ADMIN_ID), eq(feedbackId), any(ReplyFeedbackRequest.class)))
                .thenReturn(repliedFeedbackAdminResponse());

        mockMvc.perform(get("/api/admin/feedback")
                        .queryParam("type", "BUG")
                        .queryParam("status", "OPEN")
                        .queryParam("search", "login")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.first").value(true));

        mockMvc.perform(get("/api/admin/feedback/{feedbackId}", feedbackId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(LEARNER_ID))
                .andExpect(jsonPath("$.data.userEmail").value("feedback-learner@example.com"));

        mockMvc.perform(patch("/api/admin/feedback/{feedbackId}/status", feedbackId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS",
                                  "adminNote": "Checking with backend team"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật feedback thành công"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/admin/feedback/{feedbackId}/reply", feedbackId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "We are checking it"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Phản hồi feedback thành công"))
                .andExpect(jsonPath("$.data.adminReply").value("We are checking it"))
                .andExpect(jsonPath("$.data.repliedByUserId").value(ADMIN_ID));

        mockMvc.perform(delete("/api/admin/feedback/{feedbackId}", feedbackId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa feedback thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(feedbackService).deleteFeedback(feedbackId);

        when(feedbackService.getFeedback(feedbackId)).thenThrow(new AppException(ErrorCode.FEEDBACK_NOT_FOUND));

        mockMvc.perform(get("/api/admin/feedback/{feedbackId}", feedbackId)
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void adminFeedbackValidationErrorsAreMapped() throws Exception {
        mockMvc.perform(patch("/api/admin/feedback/{feedbackId}/status", feedbackId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        mockMvc.perform(patch("/api/admin/feedback/{feedbackId}/reply", feedbackId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(LEARNER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(ADMIN_ID).claim("cognito:groups", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private FeedbackResponse feedbackResponse() {
        return FeedbackResponse.builder()
                .feedbackId(feedbackId)
                .type(FeedbackType.BUG)
                .title("Login bug")
                .content("Login fails sometimes")
                .relatedUrl("https://app.example.com/login")
                .imageUrl("https://cdn.example.com/feedback/login-bug.png")
                .status(FeedbackStatus.OPEN)
                .adminReply("We are checking it")
                .repliedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .build();
    }

    private FeedbackSubmitResponse feedbackSubmitResponse() {
        return FeedbackSubmitResponse.builder()
                .feedbackId(feedbackId)
                .type(FeedbackType.BUG)
                .title("Login bug")
                .imageUrl("https://cdn.example.com/feedback/login-bug.png")
                .status(FeedbackStatus.OPEN)
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .build();
    }

    private FeedbackUploadUrlResponse uploadUrlResponse() {
        return FeedbackUploadUrlResponse.builder()
                .uploadUrl("https://s3.example.com/upload")
                .fileUrl("https://cdn.example.com/feedback/login-bug.png")
                .objectKey("feedback/learner/login-bug.png")
                .expiresAt(Instant.parse("2026-06-23T12:15:00Z"))
                .build();
    }

    private PageResponse<FeedbackAdminResponse> adminPageResponse() {
        return PageResponse.<FeedbackAdminResponse>builder()
                .items(List.of(feedbackAdminResponse()))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private FeedbackAdminResponse feedbackAdminResponse() {
        return feedbackAdminResponse(FeedbackStatus.OPEN);
    }

    private FeedbackAdminResponse feedbackAdminResponse(FeedbackStatus status) {
        return FeedbackAdminResponse.builder()
                .feedbackId(feedbackId)
                .userId(LEARNER_ID)
                .userEmail("feedback-learner@example.com")
                .userFullName("Feedback Learner")
                .type(FeedbackType.BUG)
                .title("Login bug")
                .content("Login fails sometimes")
                .relatedUrl("https://app.example.com/login")
                .imageUrl("https://cdn.example.com/feedback/login-bug.png")
                .status(status)
                .adminNote("Checking with backend team")
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .build();
    }

    private FeedbackAdminResponse repliedFeedbackAdminResponse() {
        return FeedbackAdminResponse.builder()
                .feedbackId(feedbackId)
                .userId(LEARNER_ID)
                .userEmail("feedback-learner@example.com")
                .userFullName("Feedback Learner")
                .type(FeedbackType.BUG)
                .title("Login bug")
                .content("Login fails sometimes")
                .status(FeedbackStatus.CLOSED)
                .adminReply("We are checking it")
                .repliedByUserId(ADMIN_ID)
                .repliedByFullName("Feedback Admin")
                .repliedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .build();
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
