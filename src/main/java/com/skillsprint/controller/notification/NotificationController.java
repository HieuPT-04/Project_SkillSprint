package com.skillsprint.controller.notification;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.notification.CreateReminderRequest;
import com.skillsprint.dto.response.notification.NotificationResponse;
import com.skillsprint.service.notification.NotificationService;
import com.skillsprint.service.notification.ReminderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {

    NotificationService notificationService;
    ReminderService reminderService;

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<NotificationResponse> response = notificationService.getMyNotifications(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/notifications/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyUnreadNotifications(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<NotificationResponse> response = notificationService.getMyUnreadNotifications(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId
    ) {
        NotificationResponse response = notificationService.markAsRead(jwt.getSubject(), notificationId);
        return ResponseEntity.ok(ApiResponse.success("Mark notification as read successfully", response));
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt
    ) {
        int updated = notificationService.markAllAsRead(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Mark all notifications as read successfully", updated));
    }

    @PostMapping("/workspaces/{workspaceId}/reminders")
    public ResponseEntity<ApiResponse<Void>> createReminder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateReminderRequest request
    ) {
        reminderService.createReminder(jwt.getSubject(), workspaceId, request);
        return ResponseEntity.ok(ApiResponse.success("Create reminder successfully", null));
    }
}