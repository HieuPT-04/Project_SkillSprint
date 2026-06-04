package com.skillsprint.controller.tutor;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.tutor.TutorAskRequest;
import com.skillsprint.dto.response.tutor.TutorAskResponse;
import com.skillsprint.service.tutor.AiTutorService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiTutorController {

    AiTutorService aiTutorService;

    @PostMapping("/workspaces/{workspaceId}/tutor/ask")
    public ResponseEntity<ApiResponse<TutorAskResponse>> askWorkspace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody TutorAskRequest request
    ) {
        TutorAskResponse response = aiTutorService.askWorkspace(jwt.getSubject(), workspaceId, request);
        return ResponseEntity.ok(ApiResponse.success("AI Tutor đã trả lời", response));
    }

    @PostMapping("/roadmap-steps/{stepId}/tutor/ask")
    public ResponseEntity<ApiResponse<TutorAskResponse>> ask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stepId,
            @Valid @RequestBody TutorAskRequest request
    ) {
        TutorAskResponse response = aiTutorService.ask(jwt.getSubject(), stepId, request);
        return ResponseEntity.ok(ApiResponse.success("AI Tutor đã trả lời", response));
    }
}
