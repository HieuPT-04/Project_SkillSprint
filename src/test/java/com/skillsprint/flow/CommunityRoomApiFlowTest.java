package com.skillsprint.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skillsprint.dto.request.community.CreateCommunityPinRequest;
import com.skillsprint.dto.request.community.CreateCommunityRoomInviteRequest;
import com.skillsprint.dto.request.community.CreateCommunityRoomRequest;
import com.skillsprint.dto.request.community.CreateContentReportRequest;
import com.skillsprint.dto.request.community.HideCommunityChatMessageRequest;
import com.skillsprint.dto.request.community.MuteCommunityRoomMemberRequest;
import com.skillsprint.dto.request.community.ReorderCommunityPinsRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomMemberRoleRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.dto.response.community.CommunityChatMessageResponse;
import com.skillsprint.dto.response.community.CommunityPinResponse;
import com.skillsprint.dto.response.community.CommunityRoomInviteResponse;
import com.skillsprint.dto.response.community.CommunityRoomMemberResponse;
import com.skillsprint.dto.response.community.CommunityRoomResponse;
import com.skillsprint.dto.response.community.ContentReportResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.community.CommunityPinItemType;
import com.skillsprint.enums.community.CommunityRoomInviteStatus;
import com.skillsprint.enums.community.CommunityRoomMemberStatus;
import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomRole;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.community.CommunityChatService;
import com.skillsprint.service.community.CommunityPinService;
import com.skillsprint.service.community.CommunityRoomService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityRoomApiFlowTest {

    private static final String LEARNER_ID = "community-room-learner";
    private static final String ADMIN_ID = "community-room-admin";
    private static final String TARGET_USER_ID = "community-room-target";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @MockBean
    CommunityRoomService roomService;

    @MockBean
    CommunityChatService chatService;

    @MockBean
    CommunityPinService pinService;

    @MockBean
    JwtDecoder jwtDecoder;

    UUID roomId;
    UUID memberId;
    UUID inviteId;
    UUID messageId;
    UUID pinId;
    UUID reportId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        inviteId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        pinId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(TARGET_USER_ID);
        userRepository.save(user(LEARNER_ID, "room-learner@example.com", "Room Learner"));
        userRepository.save(user(ADMIN_ID, "room-admin@example.com", "Room Admin"));
        userRepository.save(user(TARGET_USER_ID, "room-target@example.com", "Room Target"));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(LEARNER_ID);
        userRepository.deleteById(ADMIN_ID);
        userRepository.deleteById(TARGET_USER_ID);
    }

    @Test
    void anonymousUserCannotUseCommunityRoomEndpoints() throws Exception {
        mockMvc.perform(get("/api/community/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/community/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java Study Room"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/admin/community/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(roomService, never()).discoverRooms(any(), any(), any(), any(Integer.class), any(Integer.class));
        verify(roomService, never()).createRoom(any(), any());
        verify(roomService, never()).getAdminRooms(any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void learnerRoomEndpointsReturnExpectedShapes() throws Exception {
        when(roomService.createRoom(eq(LEARNER_ID), any(CreateCommunityRoomRequest.class))).thenReturn(roomResponse(true));
        when(roomService.discoverRooms(LEARNER_ID, CommunityRoomMode.PUBLIC, "java", 0, 10)).thenReturn(roomPage());
        when(roomService.getMyRooms(LEARNER_ID, 0, 10)).thenReturn(roomPage());
        when(roomService.getRoom(LEARNER_ID, roomId)).thenReturn(roomResponse(true));
        when(roomService.updateRoom(eq(LEARNER_ID), eq(roomId), any(UpdateCommunityRoomRequest.class)))
                .thenReturn(roomResponse(true));
        when(roomService.joinRoom(LEARNER_ID, roomId)).thenReturn(roomResponse(true));

        mockMvc.perform(post("/api/community/rooms")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java Study Room",
                                  "description": "Practice Java together",
                                  "mode": "PUBLIC",
                                  "maxMembers": 50
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo phòng cộng đồng thành công"))
                .andExpect(jsonPath("$.data.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.joined").value(true));

        mockMvc.perform(get("/api/community/rooms")
                        .queryParam("mode", "PUBLIC")
                        .queryParam("search", "java")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("Java Study Room"))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(get("/api/community/rooms/me")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].myRole").value("OWNER"));

        mockMvc.perform(get("/api/community/rooms/{roomId}", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner.userId").value(LEARNER_ID));

        mockMvc.perform(patch("/api/community/rooms/{roomId}", roomId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Java Room",
                                  "description": "Updated",
                                  "mode": "INVITE_ONLY",
                                  "maxMembers": 100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật phòng cộng đồng thành công"));

        mockMvc.perform(delete("/api/community/rooms/{roomId}", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa phòng cộng đồng thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/api/community/rooms/{roomId}/join", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tham gia phòng thành công"));

        mockMvc.perform(post("/api/community/rooms/{roomId}/leave", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Rời phòng thành công"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(roomService).leaveRoom(LEARNER_ID, roomId);
        verify(roomService).deleteRoom(LEARNER_ID, roomId);
    }

    @Test
    void roomMembersInvitesAndModerationEndpointsReturnExpectedShapes() throws Exception {
        when(roomService.getMembers(LEARNER_ID, roomId, 0, 10)).thenReturn(memberPage());
        when(roomService.inviteMember(eq(LEARNER_ID), eq(roomId), any(CreateCommunityRoomInviteRequest.class)))
                .thenReturn(inviteResponse(CommunityRoomInviteStatus.PENDING));
        when(roomService.getMyInvites(LEARNER_ID, 0, 10)).thenReturn(invitePage());
        when(roomService.acceptInvite(LEARNER_ID, inviteId)).thenReturn(roomResponse(true));
        when(roomService.declineInvite(LEARNER_ID, inviteId)).thenReturn(inviteResponse(CommunityRoomInviteStatus.DECLINED));
        when(roomService.updateMemberRole(eq(LEARNER_ID), eq(roomId), eq(TARGET_USER_ID), any(UpdateCommunityRoomMemberRoleRequest.class)))
                .thenReturn(memberResponse(CommunityRoomRole.MODERATOR, CommunityRoomMemberStatus.ACTIVE));
        when(roomService.muteMember(eq(LEARNER_ID), eq(roomId), eq(TARGET_USER_ID), any(MuteCommunityRoomMemberRequest.class)))
                .thenReturn(memberResponse(CommunityRoomRole.MEMBER, CommunityRoomMemberStatus.ACTIVE));
        when(roomService.banMember(LEARNER_ID, roomId, TARGET_USER_ID))
                .thenReturn(memberResponse(CommunityRoomRole.MEMBER, CommunityRoomMemberStatus.BANNED));
        when(roomService.unbanMember(LEARNER_ID, roomId, TARGET_USER_ID))
                .thenReturn(memberResponse(CommunityRoomRole.MEMBER, CommunityRoomMemberStatus.ACTIVE));

        mockMvc.perform(get("/api/community/rooms/{roomId}/members", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].memberId").value(memberId.toString()));

        mockMvc.perform(post("/api/community/rooms/{roomId}/invites", roomId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteeUserId": "%s"
                                }
                                """.formatted(TARGET_USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Gửi lời mời vào phòng thành công"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/community/rooms/invites")
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].inviteId").value(inviteId.toString()));

        mockMvc.perform(post("/api/community/rooms/invites/{inviteId}/accept", inviteId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chấp nhận lời mời thành công"));

        mockMvc.perform(post("/api/community/rooms/invites/{inviteId}/decline", inviteId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Từ chối lời mời thành công"))
                .andExpect(jsonPath("$.data.status").value("DECLINED"));

        mockMvc.perform(patch("/api/community/rooms/{roomId}/members/{targetUserId}/role", roomId, TARGET_USER_ID)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "MODERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật vai trò thành viên thành công"))
                .andExpect(jsonPath("$.data.role").value("MODERATOR"));

        mockMvc.perform(patch("/api/community/rooms/{roomId}/members/{targetUserId}/mute", roomId, TARGET_USER_ID)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minutes": 15
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mute thành viên thành công"));

        mockMvc.perform(delete("/api/community/rooms/{roomId}/members/{targetUserId}", roomId, TARGET_USER_ID)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Kick thành viên thành công"));

        verify(roomService).kickMember(LEARNER_ID, roomId, TARGET_USER_ID);

        mockMvc.perform(patch("/api/community/rooms/{roomId}/members/{targetUserId}/ban", roomId, TARGET_USER_ID)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Ban thành viên thành công"))
                .andExpect(jsonPath("$.data.status").value("BANNED"));

        mockMvc.perform(patch("/api/community/rooms/{roomId}/members/{targetUserId}/unban", roomId, TARGET_USER_ID)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Gỡ ban thành viên thành công"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void chatReportAndPinEndpointsReturnExpectedShapes() throws Exception {
        when(chatService.getHistory(LEARNER_ID, roomId, 0, 30)).thenReturn(messagePage());
        when(chatService.hideMessage(eq(LEARNER_ID), eq(roomId), eq(messageId), any(HideCommunityChatMessageRequest.class), eq(false)))
                .thenReturn(chatMessageResponse(true));
        when(chatService.reportMessage(eq(LEARNER_ID), eq(roomId), eq(messageId), any(CreateContentReportRequest.class)))
                .thenReturn(reportResponse());
        when(pinService.getPins(LEARNER_ID, roomId)).thenReturn(List.of(pinResponse()));
        when(pinService.createPin(eq(LEARNER_ID), eq(roomId), any(CreateCommunityPinRequest.class))).thenReturn(pinResponse());
        when(pinService.reorderPins(eq(LEARNER_ID), eq(roomId), any(ReorderCommunityPinsRequest.class)))
                .thenReturn(List.of(pinResponse()));

        mockMvc.perform(get("/api/community/rooms/{roomId}/messages", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].messageId").value(messageId.toString()));

        mockMvc.perform(patch("/api/community/rooms/{roomId}/messages/{messageId}/hide", roomId, messageId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hidden": true,
                                  "adminNote": "Inappropriate"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái tin nhắn thành công"))
                .andExpect(jsonPath("$.data.hidden").value(true));

        mockMvc.perform(post("/api/community/rooms/{roomId}/messages/{messageId}/report", roomId, messageId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Spam message"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Báo cáo tin nhắn thành công"))
                .andExpect(jsonPath("$.data.targetType").value("MESSAGE"));

        mockMvc.perform(get("/api/community/rooms/{roomId}/pins", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pinId").value(pinId.toString()));

        mockMvc.perform(post("/api/community/rooms/{roomId}/pins", roomId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "itemType": "ANNOUNCEMENT",
                                  "title": "Weekly focus",
                                  "content": "Practice Java streams"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo pin trong phòng thành công"))
                .andExpect(jsonPath("$.data.itemType").value("ANNOUNCEMENT"));

        mockMvc.perform(delete("/api/community/rooms/{roomId}/pins/{pinId}", roomId, pinId)
                        .with(learnerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xóa pin thành công"));

        verify(pinService).deletePin(LEARNER_ID, roomId, pinId, false);

        mockMvc.perform(patch("/api/community/rooms/{roomId}/pins/reorder", roomId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pinIds": ["%s"]
                                }
                                """.formatted(pinId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sắp xếp pin thành công"))
                .andExpect(jsonPath("$.data[0].displayOrder").value(1));
    }

    @Test
    void roomValidationAndBusinessErrorsAreMapped() throws Exception {
        mockMvc.perform(post("/api/community/rooms")
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Tiny room",
                                  "maxMembers": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());

        when(roomService.joinRoom(LEARNER_ID, roomId))
                .thenThrow(new AppException(ErrorCode.COMMUNITY_ROOM_ALREADY_JOINED));

        mockMvc.perform(post("/api/community/rooms/{roomId}/join", roomId)
                        .with(learnerJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(post("/api/community/rooms/{roomId}/pins", roomId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Missing type"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void learnerCannotUseAdminRoomEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/community/rooms")
                        .with(learnerJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(patch("/api/admin/community/rooms/{roomId}/status", roomId)
                        .with(learnerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOCKED"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(roomService, never()).getAdminRooms(any(), any(), any(), any(Integer.class), any(Integer.class));
        verify(roomService, never()).updateAdminRoomStatus(any(), any(), any());
    }

    @Test
    void adminRoomEndpointsReturnExpectedShapesAndMapValidationErrors() throws Exception {
        when(roomService.getAdminRooms(CommunityRoomStatus.ACTIVE, CommunityRoomMode.PUBLIC, "java", 0, 10))
                .thenReturn(roomPage());
        when(roomService.updateAdminRoomStatus(eq(ADMIN_ID), eq(roomId), any(UpdateCommunityRoomStatusRequest.class)))
                .thenReturn(roomResponse(false, CommunityRoomStatus.LOCKED));
        when(chatService.getAdminMessages(roomId, 0, 30)).thenReturn(messagePage());
        when(chatService.hideMessage(eq(ADMIN_ID), eq(roomId), eq(messageId), any(HideCommunityChatMessageRequest.class), eq(true)))
                .thenReturn(chatMessageResponse(true));
        when(pinService.getAdminPins(roomId)).thenReturn(List.of(pinResponse()));

        mockMvc.perform(get("/api/admin/community/rooms")
                        .queryParam("status", "ACTIVE")
                        .queryParam("mode", "PUBLIC")
                        .queryParam("search", "java")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));

        mockMvc.perform(patch("/api/admin/community/rooms/{roomId}/status", roomId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOCKED",
                                  "adminNote": "Policy review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái phòng thành công"))
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        mockMvc.perform(get("/api/admin/community/rooms/{roomId}/messages", roomId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].rawContent").value("Raw message"));

        mockMvc.perform(patch("/api/admin/community/rooms/{roomId}/messages/{messageId}/hide", roomId, messageId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hidden": true,
                                  "adminNote": "Admin hide"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái tin nhắn thành công"))
                .andExpect(jsonPath("$.data.hidden").value(true));

        mockMvc.perform(get("/api/admin/community/rooms/{roomId}/pins", roomId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pinId").value(pinId.toString()));

        mockMvc.perform(delete("/api/admin/community/rooms/{roomId}/pins/{pinId}", roomId, pinId)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Admin xóa pin thành công"));

        verify(pinService).deletePin(ADMIN_ID, roomId, pinId, true);

        mockMvc.perform(patch("/api/admin/community/rooms/{roomId}/status", roomId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor learnerJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(LEARNER_ID).claim("cognito:groups", List.of("LEARNER")))
                .authorities(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt.subject(ADMIN_ID).claim("cognito:groups", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private PageResponse<CommunityRoomResponse> roomPage() {
        return PageResponse.<CommunityRoomResponse>builder()
                .items(List.of(roomResponse(true)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<CommunityRoomMemberResponse> memberPage() {
        return PageResponse.<CommunityRoomMemberResponse>builder()
                .items(List.of(memberResponse(CommunityRoomRole.MEMBER, CommunityRoomMemberStatus.ACTIVE)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<CommunityRoomInviteResponse> invitePage() {
        return PageResponse.<CommunityRoomInviteResponse>builder()
                .items(List.of(inviteResponse(CommunityRoomInviteStatus.PENDING)))
                .page(0)
                .size(10)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private PageResponse<CommunityChatMessageResponse> messagePage() {
        return PageResponse.<CommunityChatMessageResponse>builder()
                .items(List.of(chatMessageResponse(false)))
                .page(0)
                .size(30)
                .totalItems(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();
    }

    private CommunityRoomResponse roomResponse(boolean joined) {
        return roomResponse(joined, CommunityRoomStatus.ACTIVE);
    }

    private CommunityRoomResponse roomResponse(boolean joined, CommunityRoomStatus status) {
        return CommunityRoomResponse.builder()
                .roomId(roomId)
                .name("Java Study Room")
                .description("Practice Java together")
                .mode(CommunityRoomMode.PUBLIC)
                .status(status)
                .owner(learnerAuthor())
                .maxMembers(50)
                .memberCount(3)
                .reportCount(1)
                .myRole(joined ? CommunityRoomRole.OWNER : null)
                .joined(joined)
                .banned(false)
                .adminNote(status == CommunityRoomStatus.LOCKED ? "Policy review" : null)
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private CommunityRoomMemberResponse memberResponse(
            CommunityRoomRole role,
            CommunityRoomMemberStatus status
    ) {
        return CommunityRoomMemberResponse.builder()
                .memberId(memberId)
                .roomId(roomId)
                .user(targetAuthor())
                .role(role)
                .status(status)
                .banned(status == CommunityRoomMemberStatus.BANNED)
                .muteUntil(Instant.parse("2026-06-23T12:30:00Z"))
                .joinedAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private CommunityRoomInviteResponse inviteResponse(CommunityRoomInviteStatus status) {
        return CommunityRoomInviteResponse.builder()
                .inviteId(inviteId)
                .roomId(roomId)
                .roomName("Java Study Room")
                .inviter(learnerAuthor())
                .invitee(targetAuthor())
                .status(status)
                .expiresAt(Instant.parse("2026-06-30T12:00:00Z"))
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private CommunityChatMessageResponse chatMessageResponse(boolean hidden) {
        return CommunityChatMessageResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .sender(learnerAuthor())
                .content(hidden ? "[hidden]" : "Hello room")
                .rawContent("Raw message")
                .hidden(hidden)
                .reportCount(2)
                .adminNote(hidden ? "Admin hide" : null)
                .sentAt(Instant.parse("2026-06-23T12:05:00Z"))
                .build();
    }

    private CommunityPinResponse pinResponse() {
        return CommunityPinResponse.builder()
                .pinId(pinId)
                .roomId(roomId)
                .itemType(CommunityPinItemType.ANNOUNCEMENT)
                .title("Weekly focus")
                .content("Practice Java streams")
                .pinnedBy(learnerAuthor())
                .displayOrder(1)
                .createdAt(Instant.parse("2026-06-23T12:00:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:10:00Z"))
                .build();
    }

    private ContentReportResponse reportResponse() {
        return ContentReportResponse.builder()
                .reportId(reportId)
                .targetType(ContentReportTargetType.MESSAGE)
                .targetId(messageId)
                .reporter(learnerAuthor())
                .reason("Spam message")
                .status(ContentReportStatus.PENDING)
                .createdAt(Instant.parse("2026-06-23T12:20:00Z"))
                .updatedAt(Instant.parse("2026-06-23T12:20:00Z"))
                .build();
    }

    private CommunityAuthorResponse learnerAuthor() {
        return CommunityAuthorResponse.builder()
                .userId(LEARNER_ID)
                .email("room-learner@example.com")
                .fullName("Room Learner")
                .allTimeRank(2)
                .build();
    }

    private CommunityAuthorResponse targetAuthor() {
        return CommunityAuthorResponse.builder()
                .userId(TARGET_USER_ID)
                .email("room-target@example.com")
                .fullName("Room Target")
                .allTimeRank(5)
                .build();
    }

    private User user(String userId, String email, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
