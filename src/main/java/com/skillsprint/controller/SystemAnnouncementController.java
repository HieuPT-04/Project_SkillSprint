package com.skillsprint.controller;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.common.PublicAnnouncementResponse;
import com.skillsprint.service.system.SystemAnnouncementService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/announcements")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemAnnouncementController {

    SystemAnnouncementService announcementService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<PublicAnnouncementResponse>> getActiveAnnouncement() {
        PublicAnnouncementResponse response = announcementService.getActivePublicAnnouncement();
        return ResponseEntity.ok(ApiResponse.success("Lấy thông báo công khai thành công", response));
    }
}
