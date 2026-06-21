package com.skillsprint.controller.community;

import com.skillsprint.dto.request.community.SendCommunityChatMessageRequest;
import com.skillsprint.dto.response.community.CommunityChatErrorResponse;
import com.skillsprint.dto.response.community.CommunityChatMessageResponse;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.community.CommunityChatService;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityChatWebSocketController {

    CommunityChatService chatService;
    SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/community.rooms.{roomId}.send")
    public void sendMessage(
            Principal principal,
            @DestinationVariable UUID roomId,
            @Payload SendCommunityChatMessageRequest request
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED);
        }

        CommunityChatMessageResponse response = chatService.sendMessage(principal.getName(), roomId, request);
        messagingTemplate.convertAndSend("/topic/community.rooms." + roomId, response);
    }

    @MessageExceptionHandler
    public void handleException(Throwable throwable, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return;
        }

        String message = throwable instanceof AppException appException
                ? appException.getMessage()
                : "Không thể gửi tin nhắn lúc này";
        CommunityChatErrorResponse response = CommunityChatErrorResponse.builder()
                .message(message)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", response);
    }
}
