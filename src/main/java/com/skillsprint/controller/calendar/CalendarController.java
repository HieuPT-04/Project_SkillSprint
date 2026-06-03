package com.skillsprint.controller.calendar;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.calendar.CreateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.GenerateCalendarRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskRequest;
import com.skillsprint.dto.request.calendar.UpdateCalendarTaskStatusRequest;
import com.skillsprint.dto.response.calendar.CalendarScheduleRunResponse;
import com.skillsprint.dto.response.calendar.CalendarTaskResponse;
import com.skillsprint.dto.response.calendar.EisenhowerBoardResponse;
import com.skillsprint.service.calendar.CalendarService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return ResponseEntity.ok(ApiResponse.success("Tạo lịch học thành công", response));
    }

    @GetMapping("/workspaces/{workspaceId}/calendar/tasks")
    public ResponseEntity<ApiResponse<List<CalendarTaskResponse>>> getTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        List<CalendarTaskResponse> response = calendarService.getTasks(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/workspaces/{workspaceId}/calendar/eisenhower")
    public ResponseEntity<ApiResponse<EisenhowerBoardResponse>> getEisenhowerBoard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) LocalDate date
    ) {
        EisenhowerBoardResponse response = calendarService.getEisenhowerBoard(jwt.getSubject(), workspaceId, date);
        return ResponseEntity.ok(ApiResponse.success("Lấy Eisenhower board thành công", response));
    }

    @GetMapping("/workspaces/{workspaceId}/eisenhower-tasks")
    public ResponseEntity<ApiResponse<EisenhowerBoardResponse>> getEisenhowerTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        EisenhowerBoardResponse response = calendarService.getEisenhowerTasksForWorkspace(jwt.getSubject(), workspaceId);
        return ResponseEntity.ok(ApiResponse.success("Lấy Eisenhower tasks thành công", response));
    }

    @PostMapping("/workspaces/{workspaceId}/calendar/tasks")
    public ResponseEntity<ApiResponse<CalendarTaskResponse>> createTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateCalendarTaskRequest request
    ) {
        CalendarTaskResponse response = calendarService.createTask(jwt.getSubject(), workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("Tạo task thành công", response));
    }

    @PatchMapping("/workspaces/{workspaceId}/calendar/tasks/{taskId}/status")
    public ResponseEntity<ApiResponse<CalendarTaskResponse>> updateTaskStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateCalendarTaskStatusRequest request
    ) {
        CalendarTaskResponse response = calendarService.updateTaskStatus(jwt.getSubject(), workspaceId, taskId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái task thành công", response));
    }

    @PatchMapping("/calendar/tasks/{taskId}")
    public ResponseEntity<ApiResponse<CalendarTaskResponse>> updateTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateCalendarTaskRequest request
    ) {
        CalendarTaskResponse response = calendarService.updateTask(jwt.getSubject(), taskId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật lịch học thành công", response));
    }

    @PatchMapping("/calendar/tasks/{taskId}/complete")
    public ResponseEntity<ApiResponse<CalendarTaskResponse>> completeTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId
    ) {
        CalendarTaskResponse response = calendarService.completeTask(jwt.getSubject(), taskId);
        return ResponseEntity.ok(ApiResponse.success("Hoàn thành task học thành công", response));
    }
}
