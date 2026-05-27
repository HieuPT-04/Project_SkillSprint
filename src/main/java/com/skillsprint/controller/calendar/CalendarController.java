package com.skillsprint.controller.calendar;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.calendar.GenerateCalendarRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.response.calendar.CalendarScheduleRunResponse;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.service.calendar.CalendarService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CalendarController {

    CalendarService calendarService;

    @PostMapping("/workspaces/{workspaceId}/calendar/generate")
    public ResponseEntity<ApiResponse<CalendarScheduleRunResponse>> generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody GenerateCalendarRequest request
    ) {
        CalendarScheduleRunResponse response = calendarService.generate(jwt.getSubject(), workspaceId, request);
        return ResponseEntity.ok(ApiResponse.success("Generate calendar successfully", response));
    }

    @GetMapping("/workspaces/{workspaceId}/calendar/tasks")
    public ResponseEntity<ApiResponse<List<CalendarTaskResponse>>> getTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        List<CalendarTaskResponse> response = calendarService.getTasks(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/calendar/tasks/{taskId}")
    public ResponseEntity<ApiResponse<CalendarTaskResponse>> updateTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateCalendarTaskRequest request
    ) {
        CalendarTaskResponse response = calendarService.updateTask(jwt.getSubject(), taskId, request);
        return ResponseEntity.ok(ApiResponse.success("Update calendar task successfully", response));
    }

    @PatchMapping("/calendar/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<CalendarTaskResponse>> completeTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId
    ) {
        CalendarTaskResponse response = calendarService.completeTask(jwt.getSubject(), taskId);
        return ResponseEntity.ok(ApiResponse.success("Complete calendar task successfully", response));
    }
}
