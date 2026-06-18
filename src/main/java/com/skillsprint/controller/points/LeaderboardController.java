package com.skillsprint.controller.points;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.points.LeaderboardResponse;
import com.skillsprint.dto.response.points.MyPointsResponse;
import com.skillsprint.dto.response.points.PointEventResponse;
import com.skillsprint.enums.points.LeaderboardPeriod;
import com.skillsprint.service.points.PointService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaderboardController {

    PointService pointService;

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getWeeklyLeaderboard(
            @RequestParam(defaultValue = "20") int size
    ) {
        LeaderboardResponse response = pointService.getLeaderboard(LeaderboardPeriod.WEEKLY, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy bảng xếp hạng tuần thành công", response));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getMonthlyLeaderboard(
            @RequestParam(defaultValue = "20") int size
    ) {
        LeaderboardResponse response = pointService.getLeaderboard(LeaderboardPeriod.MONTHLY, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy bảng xếp hạng tháng thành công", response));
    }

    @GetMapping("/all-time")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getAllTimeLeaderboard(
            @RequestParam(defaultValue = "20") int size
    ) {
        LeaderboardResponse response = pointService.getLeaderboard(LeaderboardPeriod.ALL_TIME, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy bảng xếp hạng tổng thành công", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyPointsResponse>> getMyPoints(@AuthenticationPrincipal Jwt jwt) {
        MyPointsResponse response = pointService.getMyPoints(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Lấy điểm của tôi thành công", response));
    }

    @GetMapping("/me/events")
    public ResponseEntity<ApiResponse<List<PointEventResponse>>> getMyPointEvents(@AuthenticationPrincipal Jwt jwt) {
        List<PointEventResponse> response = pointService.getMyPointEvents(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử điểm thành công", response));
    }
}
