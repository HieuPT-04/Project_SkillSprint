package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.admin.AdminLeaderboardResponse;
import com.skillsprint.dto.response.admin.AdminPointEventResponse;
import com.skillsprint.dto.response.admin.AdminUserPointSummaryResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.enums.points.LeaderboardPeriod;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.service.points.AdminLeaderboardService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminLeaderboardController {

    AdminLeaderboardService adminLeaderboardService;

    @GetMapping("/leaderboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminLeaderboardResponse>> getLeaderboard(
            @RequestParam(defaultValue = "WEEKLY") LeaderboardPeriod period,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AdminLeaderboardResponse response = adminLeaderboardService.getLeaderboard(period, search, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy bảng xếp hạng admin thành công", response));
    }

    @GetMapping("/users/{userId}/points")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserPointSummaryResponse>> getUserPointSummary(
            @PathVariable String userId
    ) {
        AdminUserPointSummaryResponse response = adminLeaderboardService.getUserPointSummary(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy tổng quan điểm người dùng thành công", response));
    }

    @GetMapping("/users/{userId}/point-events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminPointEventResponse>>> getUserPointEvents(
            @PathVariable String userId,
            @RequestParam(required = false) PointEventType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<AdminPointEventResponse> response = adminLeaderboardService
                .getUserPointEvents(userId, type, from, to, workspaceId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử điểm người dùng thành công", response));
    }
}
