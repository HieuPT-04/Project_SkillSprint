package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.community.HideCommunityChatMessageRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityChatMessageResponse;
import com.skillsprint.dto.response.community.CommunityRoomResponse;
import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.service.community.CommunityChatService;
import com.skillsprint.service.community.CommunityPinService;
import com.skillsprint.service.community.CommunityRoomService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/community/rooms")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCommunityRoomController {

    CommunityRoomService roomService;
    CommunityChatService chatService;
    CommunityPinService pinService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CommunityRoomResponse>>> getRooms(
            @RequestParam(required = false) CommunityRoomStatus status,
            @RequestParam(required = false) CommunityRoomMode mode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<CommunityRoomResponse> response =
                roomService.getAdminRooms(status, mode, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{roomId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommunityRoomResponse>> updateRoomStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateCommunityRoomStatusRequest request
    ) {
        CommunityRoomResponse response = roomService.updateAdminRoomStatus(jwt.getSubject(), roomId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái phòng thành công", response));
    }

    @GetMapping("/{roomId}/messages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CommunityChatMessageResponse>>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        PageResponse<CommunityChatMessageResponse> response =
                chatService.getAdminMessages(roomId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{roomId}/messages/{messageId}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CommunityChatMessageResponse>> hideMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable UUID messageId,
            @Valid @RequestBody HideCommunityChatMessageRequest request
    ) {
        CommunityChatMessageResponse response =
                chatService.hideMessage(jwt.getSubject(), roomId, messageId, request, true);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái tin nhắn thành công", response));
    }

    @DeleteMapping("/{roomId}/pins/{pinId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable UUID pinId
    ) {
        pinService.deletePin(jwt.getSubject(), roomId, pinId, true);
        return ResponseEntity.ok(ApiResponse.success("Admin xóa pin thành công", null));
    }
}
