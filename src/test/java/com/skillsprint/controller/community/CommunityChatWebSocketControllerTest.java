package com.skillsprint.controller.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.request.community.SendCommunityChatMessageRequest;
import com.skillsprint.dto.response.community.CommunityChatErrorResponse;
import com.skillsprint.dto.response.community.CommunityChatMessageResponse;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.community.CommunityChatService;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class CommunityChatWebSocketControllerTest {

    private static final String USER_ID = "ws-user-1";

    @Mock
    CommunityChatService chatService;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    CommunityChatWebSocketController controller;

    @Test
    void sendMessageBroadcastsSavedMessageToRoomTopic() {
        UUID roomId = UUID.randomUUID();
        SendCommunityChatMessageRequest request = chatRequest("Hello realtime room");
        CommunityChatMessageResponse response = chatMessage(roomId);
        when(chatService.sendMessage(USER_ID, roomId, request)).thenReturn(response);

        controller.sendMessage(principal(USER_ID), roomId, request);

        verify(chatService).sendMessage(USER_ID, roomId, request);
        verify(messagingTemplate).convertAndSend("/topic/community.rooms." + roomId, response);
    }

    @Test
    void sendMessageRequiresAuthenticatedPrincipal() {
        UUID roomId = UUID.randomUUID();
        SendCommunityChatMessageRequest request = chatRequest("blocked");

        AppException exception = assertThrows(
                AppException.class,
                () -> controller.sendMessage(principal(" "), roomId, request)
        );

        assertSame(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(chatService, messagingTemplate);
    }

    @Test
    void sendMessageRejectsMissingPrincipalAndMissingPrincipalName() {
        UUID roomId = UUID.randomUUID();
        SendCommunityChatMessageRequest request = chatRequest("blocked");

        AppException missingPrincipal = assertThrows(
                AppException.class,
                () -> controller.sendMessage(null, roomId, request)
        );
        AppException missingName = assertThrows(
                AppException.class,
                () -> controller.sendMessage(principal(null), roomId, request)
        );

        assertSame(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED, missingPrincipal.getErrorCode());
        assertSame(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED, missingName.getErrorCode());
        verifyNoInteractions(chatService, messagingTemplate);
    }

    @Test
    void handleExceptionSendsBusinessErrorToAuthenticatedUserQueue() {
        AppException failure = new AppException(ErrorCode.COMMUNITY_CHAT_MEMBER_MUTED);

        controller.handleException(failure, principal(USER_ID));

        ArgumentCaptor<CommunityChatErrorResponse> responseCaptor =
                ArgumentCaptor.forClass(CommunityChatErrorResponse.class);
        verify(messagingTemplate)
                .convertAndSendToUser(eq(USER_ID), eq("/queue/errors"), responseCaptor.capture());
        CommunityChatErrorResponse response = responseCaptor.getValue();
        assertEquals(ErrorCode.COMMUNITY_CHAT_MEMBER_MUTED.getMessage(), response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void handleExceptionSendsGenericErrorForUnexpectedFailure() {
        controller.handleException(new IllegalStateException("boom"), principal(USER_ID));

        ArgumentCaptor<CommunityChatErrorResponse> responseCaptor =
                ArgumentCaptor.forClass(CommunityChatErrorResponse.class);
        verify(messagingTemplate)
                .convertAndSendToUser(eq(USER_ID), eq("/queue/errors"), responseCaptor.capture());
        assertEquals("Không thể gửi tin nhắn lúc này", responseCaptor.getValue().getMessage());
    }

    @Test
    void handleExceptionDoesNothingWhenPrincipalIsMissing() {
        controller.handleException(new AppException(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED), null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleExceptionDoesNothingWhenPrincipalNameIsMissing() {
        controller.handleException(new AppException(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED), principal(null));

        verifyNoInteractions(messagingTemplate);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private SendCommunityChatMessageRequest chatRequest(String content) {
        SendCommunityChatMessageRequest request = new SendCommunityChatMessageRequest();
        request.setContent(content);
        return request;
    }

    private CommunityChatMessageResponse chatMessage(UUID roomId) {
        return CommunityChatMessageResponse.builder()
                .messageId(UUID.randomUUID())
                .roomId(roomId)
                .content("Hello realtime room")
                .sentAt(Instant.parse("2026-06-23T10:15:30Z"))
                .build();
    }
}
