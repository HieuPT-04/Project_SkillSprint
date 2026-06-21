package com.skillsprint.controller.community;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.community.CreateCommunityRoomInviteRequest;
import com.skillsprint.dto.request.community.CreateCommunityRoomRequest;
import com.skillsprint.dto.request.community.MuteCommunityRoomMemberRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomMemberRoleRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityRoomInviteResponse;
import com.skillsprint.dto.response.community.CommunityRoomMemberResponse;
import com.skillsprint.dto.response.community.CommunityRoomResponse;
import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.service.community.CommunityRoomService;
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
@RequestMapping("/api/community/rooms")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityRoomController {

    CommunityRoomService roomService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityRoomResponse>> createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCommunityRoomRequest request
    ) {
        CommunityRoomResponse response = roomService.createRoom(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo phòng cộng đồng thành công", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CommunityRoomResponse>>> discoverRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) CommunityRoomMode mode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityRoomResponse> response =
                roomService.discoverRooms(jwt.getSubject(), mode, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<CommunityRoomResponse>>> getMyRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityRoomResponse> response =
                roomService.getMyRooms(jwt.getSubject(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<CommunityRoomResponse>> getRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        CommunityRoomResponse response = roomService.getRoom(jwt.getSubject(), roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<ApiResponse<CommunityRoomResponse>> updateRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateCommunityRoomRequest request
    ) {
        CommunityRoomResponse response = roomService.updateRoom(jwt.getSubject(), roomId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật phòng cộng đồng thành công", response));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        roomService.deleteRoom(jwt.getSubject(), roomId);
        return ResponseEntity.ok(ApiResponse.success("Xóa phòng cộng đồng thành công", null));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<CommunityRoomResponse>> joinRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        CommunityRoomResponse response = roomService.joinRoom(jwt.getSubject(), roomId);
        return ResponseEntity.ok(ApiResponse.success("Tham gia phòng thành công", response));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        roomService.leaveRoom(jwt.getSubject(), roomId);
        return ResponseEntity.ok(ApiResponse.success("Rời phòng thành công", null));
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<PageResponse<CommunityRoomMemberResponse>>> getMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityRoomMemberResponse> response =
                roomService.getMembers(jwt.getSubject(), roomId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{roomId}/invites")
    public ResponseEntity<ApiResponse<CommunityRoomInviteResponse>> inviteMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateCommunityRoomInviteRequest request
    ) {
        CommunityRoomInviteResponse response = roomService.inviteMember(jwt.getSubject(), roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gửi lời mời vào phòng thành công", response));
    }

    @GetMapping("/invites")
    public ResponseEntity<ApiResponse<PageResponse<CommunityRoomInviteResponse>>> getMyInvites(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityRoomInviteResponse> response =
                roomService.getMyInvites(jwt.getSubject(), page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/invites/{inviteId}/accept")
    public ResponseEntity<ApiResponse<CommunityRoomResponse>> acceptInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID inviteId
    ) {
        CommunityRoomResponse response = roomService.acceptInvite(jwt.getSubject(), inviteId);
        return ResponseEntity.ok(ApiResponse.success("Chấp nhận lời mời thành công", response));
    }

    @PostMapping("/invites/{inviteId}/decline")
    public ResponseEntity<ApiResponse<CommunityRoomInviteResponse>> declineInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID inviteId
    ) {
        CommunityRoomInviteResponse response = roomService.declineInvite(jwt.getSubject(), inviteId);
        return ResponseEntity.ok(ApiResponse.success("Từ chối lời mời thành công", response));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/role")
    public ResponseEntity<ApiResponse<CommunityRoomMemberResponse>> updateMemberRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable String targetUserId,
            @Valid @RequestBody UpdateCommunityRoomMemberRoleRequest request
    ) {
        CommunityRoomMemberResponse response =
                roomService.updateMemberRole(jwt.getSubject(), roomId, targetUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò thành viên thành công", response));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/mute")
    public ResponseEntity<ApiResponse<CommunityRoomMemberResponse>> muteMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable String targetUserId,
            @Valid @RequestBody MuteCommunityRoomMemberRequest request
    ) {
        CommunityRoomMemberResponse response =
                roomService.muteMember(jwt.getSubject(), roomId, targetUserId, request);
        return ResponseEntity.ok(ApiResponse.success("Mute thành viên thành công", response));
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable String targetUserId
    ) {
        roomService.kickMember(jwt.getSubject(), roomId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Kick thành viên thành công", null));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/ban")
    public ResponseEntity<ApiResponse<CommunityRoomMemberResponse>> banMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable String targetUserId
    ) {
        CommunityRoomMemberResponse response = roomService.banMember(jwt.getSubject(), roomId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Ban thành viên thành công", response));
    }

    @PatchMapping("/{roomId}/members/{targetUserId}/unban")
    public ResponseEntity<ApiResponse<CommunityRoomMemberResponse>> unbanMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable String targetUserId
    ) {
        CommunityRoomMemberResponse response = roomService.unbanMember(jwt.getSubject(), roomId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Gỡ ban thành viên thành công", response));
    }
}
