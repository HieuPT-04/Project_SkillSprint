package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.community.CreateBlacklistKeywordRequest;
import com.skillsprint.dto.request.community.UpdateCommunityPostStatusRequest;
import com.skillsprint.dto.request.community.UpdateContentReportStatusRequest;
import com.skillsprint.dto.request.community.UpdatePostCommentStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.BlacklistKeywordResponse;
import com.skillsprint.dto.response.community.CommunityPostResponse;
import com.skillsprint.dto.response.community.ContentReportResponse;
import com.skillsprint.dto.response.community.PostCommentResponse;
import com.skillsprint.enums.community.CommunityPostStatus;
import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import com.skillsprint.enums.community.PostCommentStatus;
import com.skillsprint.service.community.CommunityBlacklistService;
import com.skillsprint.service.community.CommunityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/community")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCommunityController {

    CommunityService communityService;
    CommunityBlacklistService blacklistService;

    @GetMapping("/moderation/posts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CommunityPostResponse>>> getPosts(
            @RequestParam(required = false) CommunityPostStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityPostResponse> response =
                communityService.getAdminPosts(status, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/moderation/comments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PostCommentResponse>>> getComments(
            @RequestParam(required = false) PostCommentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PostCommentResponse> response =
                communityService.getAdminComments(status, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContentReportResponse>>> getReports(
            @RequestParam(required = false) ContentReportTargetType targetType,
            @RequestParam(required = false) ContentReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<ContentReportResponse> response =
                communityService.getAdminReports(targetType, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/posts/{postId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> updatePostStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdateCommunityPostStatusRequest request
    ) {
        CommunityPostResponse response = communityService.updatePostStatus(jwt.getSubject(), postId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái bài viết thành công", response));
    }

    @PatchMapping("/comments/{commentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PostCommentResponse>> updateCommentStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdatePostCommentStatusRequest request
    ) {
        PostCommentResponse response = communityService.updateCommentStatus(jwt.getSubject(), commentId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái bình luận thành công", response));
    }

    @PatchMapping("/reports/{reportId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ContentReportResponse>> updateReportStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateContentReportStatusRequest request
    ) {
        ContentReportResponse response = communityService.updateReportStatus(jwt.getSubject(), reportId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái report thành công", response));
    }

    @GetMapping("/blacklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BlacklistKeywordResponse>>> getBlacklist() {
        return ResponseEntity.ok(ApiResponse.success(blacklistService.getKeywords()));
    }

    @PostMapping("/blacklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BlacklistKeywordResponse>> addBlacklistKeyword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBlacklistKeywordRequest request
    ) {
        BlacklistKeywordResponse response = blacklistService.addKeyword(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Thêm từ khóa blacklist thành công", response));
    }

    @DeleteMapping("/blacklist/{wordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBlacklistKeyword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long wordId
    ) {
        blacklistService.deleteKeyword(jwt.getSubject(), wordId);
        return ResponseEntity.ok(ApiResponse.success("Xóa từ khóa blacklist thành công", null));
    }
}
