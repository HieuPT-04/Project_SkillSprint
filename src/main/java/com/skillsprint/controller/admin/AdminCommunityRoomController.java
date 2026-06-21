package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.community.UpdateCommunityRoomStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityRoomResponse;
import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomStatus;
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
}
