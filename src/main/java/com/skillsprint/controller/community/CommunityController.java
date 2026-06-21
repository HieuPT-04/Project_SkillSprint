package com.skillsprint.controller.community;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.community.CreateCommunityPostRequest;
import com.skillsprint.dto.request.community.CreateContentReportRequest;
import com.skillsprint.dto.request.community.CreatePostCommentRequest;
import com.skillsprint.dto.request.community.UpdateCommunityPostRequest;
import com.skillsprint.dto.request.community.UpdatePostCommentRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityPostResponse;
import com.skillsprint.dto.response.community.ContentReportResponse;
import com.skillsprint.dto.response.community.PostCommentResponse;
import com.skillsprint.service.community.CommunityService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/community")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityController {

    CommunityService communityService;

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> createPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCommunityPostRequest request
    ) {
        CommunityPostResponse response = communityService.createPost(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo bài viết cộng đồng thành công", response));
    }

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PageResponse<CommunityPostResponse>>> getFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityPostResponse> response =
                communityService.getFeed(jwt.getSubject(), search, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> getPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ) {
        CommunityPostResponse response = communityService.getPost(jwt.getSubject(), postId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> updatePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody UpdateCommunityPostRequest request
    ) {
        CommunityPostResponse response = communityService.updatePost(jwt.getSubject(), postId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bài viết thành công", response));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ) {
        communityService.deletePost(jwt.getSubject(), postId);
        return ResponseEntity.ok(ApiResponse.success("Xóa bài viết thành công", null));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> likePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ) {
        CommunityPostResponse response = communityService.likePost(jwt.getSubject(), postId);
        return ResponseEntity.ok(ApiResponse.success("Like bài viết thành công", response));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<ApiResponse<CommunityPostResponse>> unlikePost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId
    ) {
        CommunityPostResponse response = communityService.unlikePost(jwt.getSubject(), postId);
        return ResponseEntity.ok(ApiResponse.success("Bỏ like bài viết thành công", response));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<PostCommentResponse>>> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<PostCommentResponse> response = communityService.getComments(postId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PostCommentResponse>> createComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody CreatePostCommentRequest request
    ) {
        PostCommentResponse response = communityService.createComment(jwt.getSubject(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo bình luận thành công", response));
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<PostCommentResponse>> updateComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdatePostCommentRequest request
    ) {
        PostCommentResponse response = communityService.updateComment(jwt.getSubject(), commentId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bình luận thành công", response));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID commentId
    ) {
        communityService.deleteComment(jwt.getSubject(), commentId);
        return ResponseEntity.ok(ApiResponse.success("Xóa bình luận thành công", null));
    }

    @PostMapping("/posts/{postId}/report")
    public ResponseEntity<ApiResponse<ContentReportResponse>> reportPost(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateContentReportRequest request
    ) {
        ContentReportResponse response = communityService.reportPost(jwt.getSubject(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Báo cáo bài viết thành công", response));
    }

    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<ApiResponse<ContentReportResponse>> reportComment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID commentId,
            @Valid @RequestBody CreateContentReportRequest request
    ) {
        ContentReportResponse response = communityService.reportComment(jwt.getSubject(), commentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Báo cáo bình luận thành công", response));
    }
}
