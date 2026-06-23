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

import com.skillsprint.dto.request.community.CreateBlacklistKeywordRequest;
import com.skillsprint.dto.request.community.CreateCommunityPostRequest;
import com.skillsprint.dto.request.community.CreateContentReportRequest;
import com.skillsprint.dto.request.community.CreatePostCommentRequest;
import com.skillsprint.dto.request.community.UpdateCommunityPostRequest;
import com.skillsprint.dto.request.community.UpdateCommunityPostStatusRequest;
import com.skillsprint.dto.request.community.UpdateContentReportStatusRequest;
import com.skillsprint.dto.request.community.UpdatePostCommentRequest;
import com.skillsprint.dto.request.community.UpdatePostCommentStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.BlacklistKeywordResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.dto.response.community.CommunityPostResponse;
import com.skillsprint.dto.response.community.CommunityUserPostResponse;
import com.skillsprint.dto.response.community.ContentReportResponse;
import com.skillsprint.dto.response.community.PostCommentResponse;
import com.skillsprint.dto.response.community.PostCommentUserResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.community.CommunityPostStatus;
import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import com.skillsprint.enums.community.PostCommentStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.community.CommunityBlacklistService;
import com.skillsprint.service.community.CommunityService;
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
class CommunityCoreApiFlowTest {

    private static final String LEARNER_ID = "community-core-learner";
    private static final String ADMIN_ID = "community-core-admin";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    CommunityService communityService;

    @MockBean
    CommunityBlacklistService blacklistService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID postId;
    UUID commentId;
    UUID reportId;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        commentId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(ADMIN_ID);
        userRepository.save(user(LEARNER_ID, "community-learner@example.com", "Community Learner"));
        userRepository.save(user(ADMIN_ID, "community-admin@example.com", "Community Admin"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(ADMIN_ID);
    }

    @Test
    void anonymousUserCannotUseCommunityEndpoints() throws Exception {
        mockMvc.perform(get("/api/community/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Hello community"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/admin/community/moderation/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(communityService, never()).getFeed(any(), any(), any(), any(Integer.class), any(Integer.class));
        verify(communityService, never()).createPost(any(), any());
        verify(communityService, never()).getAdminPosts(any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void learnerPostCommentAndReportEndpointsReturnExpectedShapes() throws Exception {
        when(communityService.createPost(eq(LEARNER_ID), any(CreateCommunityPostRequest.class)))
                .thenReturn(userPostResponse(false));
        when(communityService.getFeed(LEARNER_ID, "java", "spring", 0, 10))
                .thenReturn(userPostPage());
        when(communityService.getMyPosts(LEARNER_ID, CommunityPostStatus.APPROVED, 0, 10))
                .thenReturn(userPostPage());
        when(communityService.getPost(LEARNER_ID, postId)).thenReturn(userPostResponse(false));
        when(communityService.updatePost(eq(LEARNER_ID), eq(postId), any(UpdateCommunityPostRequest.class)))
                .thenReturn(userPostResponse(false));
        when(communityService.likePost(LEARNER_ID, postId)).thenReturn(userPostResponse(true));
        when(communityService.unlikePost(LEARNER_ID, postId)).thenReturn(userPostResponse(false));
        when(communityService.getComments(LEARNER_ID, postId, 0, 10)).thenReturn(userCommentPage());
        when(communityService.createComment(eq(LEARNER_ID), eq(postId), any(CreatePostCommentRequest.class)))
                .thenReturn(userCommentResponse());
        when(communityService.updateComment(eq(LEARNER_ID), eq(commentId), any(UpdatePostCommentRequest.class)))
                .thenReturn(userCommentResponse());
        when(communityService.reportPost(eq(LEARNER_ID), eq(postId), any(CreateContentReportRequest.class)))
                .thenReturn(reportResponse(ContentReportTargetType.POST, postId));
        when(communityService.reportComment(eq(LEARNER_ID), eq(commentId), any(CreateContentReportRequest.class)))
                .thenReturn(reportResponse(ContentReportTargetType.COMMENT, commentId));

        mockMvc.perform(post("/api/community/posts")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Learning Spring Boot testing",
                                  "hashtags": ["spring", "testing"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo bài viết cộng đồng thành công"))
                .andExpect(jsonPath("$.data.postId").value(postId.toString()));

        mockMvc.perform(get("/api/community/posts")
                        .queryParam("search", "java")
                        .queryParam("hashtag", "spring")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("Learning Spring Boot testing"))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(get("/api/community/posts/me")
                        .queryParam("status", "APPROVED")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("APPROVED"));

        mockMvc.perform(get("/api/community/posts/{postId}", postId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.userId").value(LEARNER_ID));

        mockMvc.perform(patch("/api/community/posts/{postId}", postId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated testing post",
                                  "hashtags": ["updated"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật bài viết thành công"));

        mockMvc.perform(post("/api/community/posts/{postId}/like", postId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Like bài viết thành công"))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        mockMvc.perform(delete("/api/community/posts/{postId}/like", postId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bỏ like bài viết thành công"))
                .andExpect(jsonPath("$.data.likedByMe").value(false));

        mockMvc.perform(get("/api/community/posts/{postId}/comments", postId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].commentId").value(commentId.toString()));

        mockMvc.perform(post("/api/community/posts/{postId}/comments", postId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Useful post"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo bình luận thành công"));

        mockMvc.perform(patch("/api/community/comments/{commentId}", commentId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Updated comment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật bình luận thành công"));

        mockMvc.perform(post("/api/community/posts/{postId}/report", postId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Spam content"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Báo cáo bài viết thành công"))
                .andExpect(jsonPath("$.data.targetType").value("POST"));

        mockMvc.perform(post("/api/community/comments/{commentId}/report", commentId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Harassment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Báo cáo bình luận thành công"))
                .andExpect(jsonPath("$.data.targetType").value("COMMENT"));
    }

    @Test
    void learnerCommunityValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "ok",
                                  "hashtags": ["one","two","three","four","five","six","seven","eight","nine","ten","eleven"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        when(communityService.getPost(LEARNER_ID, postId))
                .thenThrow(new AppException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        mockMvc.perform(get("/api/community/posts/{postId}", postId)
                        .with(learnerJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));

        when(communityService.reportPost(eq(LEARNER_ID), eq(postId), any(CreateContentReportRequest.class)))
                .thenThrow(new AppException(ErrorCode.COMMUNITY_REPORT_DUPLICATED));

        mockMvc.perform(post("/api/community/posts/{postId}/report", postId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Already reported"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void deletePostAndCommentEndpointsReturnSuccessMessages() throws Exception {
        mockMvc.perform(delete("/api/community/posts/{postId}", postId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa bài viết thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(delete("/api/community/comments/{commentId}", commentId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa bình luận thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(communityService).deletePost(LEARNER_ID, postId);
        verify(communityService).deleteComment(LEARNER_ID, commentId);
    }

    @Test
    void learnerCannotUseAdminCommunityEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/community/moderation/posts")
                        .with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/admin/community/blacklist")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "spam"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(communityService, never()).getAdminPosts(any(), any(), any(Integer.class), any(Integer.class));
        verify(blacklistService, never()).addKeyword(any(), any());
    }

    @Test
    void adminModerationAndBlacklistEndpointsReturnExpectedShapes() throws Exception {
        when(communityService.getAdminPosts(CommunityPostStatus.PENDING_MODERATION, "spring", 0, 10))
                .thenReturn(adminPostPage());
        when(communityService.getAdminComments(PostCommentStatus.PENDING_MODERATION, "comment", 0, 10))
                .thenReturn(adminCommentPage());
        when(communityService.getAdminReports(ContentReportTargetType.POST, ContentReportStatus.PENDING, 0, 10))
                .thenReturn(reportPage());
        when(communityService.updatePostStatus(eq(ADMIN_ID), eq(postId), any(UpdateCommunityPostStatusRequest.class)))
                .thenReturn(adminPostResponse(CommunityPostStatus.HIDDEN));
        when(communityService.updateCommentStatus(eq(ADMIN_ID), eq(commentId), any(UpdatePostCommentStatusRequest.class)))
                .thenReturn(adminCommentResponse(PostCommentStatus.HIDDEN));
        when(communityService.updateReportStatus(eq(ADMIN_ID), eq(reportId), any(UpdateContentReportStatusRequest.class)))
                .thenReturn(reportResponse(ContentReportTargetType.POST, postId, ContentReportStatus.REVIEWED));
        when(blacklistService.getKeywords()).thenReturn(List.of(blacklistKeywordResponse()));
        when(blacklistService.addKeyword(eq(ADMIN_ID), any(CreateBlacklistKeywordRequest.class)))
                .thenReturn(blacklistKeywordResponse());

        mockMvc.perform(get("/api/admin/community/moderation/posts")
                        .queryParam("status", "PENDING_MODERATION")
                        .queryParam("search", "spring")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(postId.toString()))
                .andExpect(jsonPath("$.data.items[0].reportCount").value(2));

        mockMvc.perform(get("/api/admin/community/moderation/comments")
                        .queryParam("status", "PENDING_MODERATION")
                        .queryParam("search", "comment")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].commentId").value(commentId.toString()));

        mockMvc.perform(get("/api/admin/community/reports")
                        .queryParam("targetType", "POST")
                        .queryParam("status", "PENDING")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"));

        mockMvc.perform(patch("/api/admin/community/posts/{postId}/status", postId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "HIDDEN",
                                  "adminNote": "Contains spam"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái bài viết thành công"))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(patch("/api/admin/community/comments/{commentId}/status", commentId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "HIDDEN",
                                  "adminNote": "Off-topic"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái bình luận thành công"))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(patch("/api/admin/community/reports/{reportId}/status", reportId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REVIEWED",
                                  "adminNote": "Handled"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái report thành công"))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"));

        mockMvc.perform(get("/api/admin/community/blacklist")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyword").value("spam"));

        mockMvc.perform(post("/api/admin/community/blacklist")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "spam"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Thêm từ khóa blacklist thành công"))
                .andExpect(jsonPath("$.data.wordId").value(7));

        mockMvc.perform(delete("/api/admin/community/blacklist/{wordId}", 7)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa từ khóa blacklist thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(blacklistService).deleteKeyword(ADMIN_ID, 7L);
    }

    @Test
    void adminModerationValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(patch("/api/admin/community/posts/{postId}/status", postId)
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

        when(blacklistService.addKeyword(eq(ADMIN_ID), any(CreateBlacklistKeywordRequest.class)))
                .thenThrow(new AppException(ErrorCode.BLACKLIST_KEYWORD_DUPLICATED));

        mockMvc.perform(post("/api/admin/community/blacklist")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keyword": "spam"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));
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

    private PageResponse<CommunityUserPostResponse> userPostPage() {
        return PageResponse.<CommunityUserPostResponse>builder()
                .items(List.of(userPostResponse(false)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<PostCommentUserResponse> userCommentPage() {
        return PageResponse.<PostCommentUserResponse>builder()
                .items(List.of(userCommentResponse()))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<CommunityPostResponse> adminPostPage() {
        return PageResponse.<CommunityPostResponse>builder()
                .items(List.of(adminPostResponse(CommunityPostStatus.PENDING_MODERATION)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<PostCommentResponse> adminCommentPage() {
        return PageResponse.<PostCommentResponse>builder()
                .items(List.of(adminCommentResponse(PostCommentStatus.PENDING_MODERATION)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<ContentReportResponse> reportPage() {
        return PageResponse.<ContentReportResponse>builder()
                .items(List.of(reportResponse(ContentReportTargetType.POST, postId)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private CommunityUserPostResponse userPostResponse(boolean likedByMe) {
        return CommunityUserPostResponse.builder()
                .postId(postId)
                .author(learnerAuthor())
                .content("Learning Spring Boot testing")
                .hashtags(List.of("spring", "testing"))
                .status(CommunityPostStatus.APPROVED)
                .likeCount(likedByMe ? 2 : 1)
                .commentCount(1)
                .likedByMe(likedByMe)
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private CommunityPostResponse adminPostResponse(CommunityPostStatus status) {
        return CommunityPostResponse.builder()
                .postId(postId)
                .author(learnerAuthor())
                .content("Learning Spring Boot testing")
                .hashtags(List.of("spring", "testing"))
                .status(status)
                .likeCount(1)
                .commentCount(1)
                .reportCount(2)
                .likedByMe(false)
                .adminNote(status == CommunityPostStatus.HIDDEN ? "Contains spam" : null)
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private PostCommentUserResponse userCommentResponse() {
        return PostCommentUserResponse.builder()
                .commentId(commentId)
                .postId(postId)
                .author(learnerAuthor())
                .content("Useful post")
                .status(PostCommentStatus.VISIBLE)
                .createdAt(Instant.parse("2026-06-23T12:05:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private PostCommentResponse adminCommentResponse(PostCommentStatus status) {
        return PostCommentResponse.builder()
                .commentId(commentId)
                .postId(postId)
                .author(learnerAuthor())
                .content("Useful post")
                .status(status)
                .reportCount(1)
                .adminNote(status == PostCommentStatus.HIDDEN ? "Off-topic" : null)
                .createdAt(Instant.parse("2026-06-23T12:05:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private ContentReportResponse reportResponse(ContentReportTargetType targetType, UUID targetId) {
        return reportResponse(targetType, targetId, ContentReportStatus.PENDING);
    }

    private ContentReportResponse reportResponse(
            ContentReportTargetType targetType,
            UUID targetId,
            ContentReportStatus status
    ) {
        return ContentReportResponse.builder()
                .reportId(reportId)
                .targetType(targetType)
                .targetId(targetId)
                .reporter(learnerAuthor())
                .reason("Spam content")
                .status(status)
                .adminNote(status == ContentReportStatus.REVIEWED ? "Handled" : null)
                .reviewedAt(status == ContentReportStatus.REVIEWED ? Instant.parse("2026-06-23T12:30:00Z") : null)
                .createdAt(Instant.parse("2026-06-23T12:20:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:30:00Z"))
                .build();
    }

    private BlacklistKeywordResponse blacklistKeywordResponse() {
        return BlacklistKeywordResponse.builder()
                .wordId(7L)
                .keyword("spam")
                .createdBy(adminAuthor())
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:00:00Z"))
                .build();
    }

    private CommunityAuthorResponse learnerAuthor() {
        return CommunityAuthorResponse.builder()
                .userId(LEARNER_ID)
                .email("community-learner@example.com")
                .fullName("Community Learner")
                .allTimeRank(3)
                .build();
    }

    private CommunityAuthorResponse adminAuthor() {
        return CommunityAuthorResponse.builder()
                .userId(ADMIN_ID)
                .email("community-admin@example.com")
                .fullName("Community Admin")
                .allTimeRank(1)
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
