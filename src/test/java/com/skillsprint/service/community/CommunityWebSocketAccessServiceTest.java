package com.skillsprint.service.community;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.enums.community.CommunityRoomMemberStatus;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CommunityRoomMemberRepository;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityWebSocketAccessServiceTest {

    @Mock QuotaService quotaService;
    @Mock CommunityRoomMemberRepository roomMemberRepository;

    CommunityWebSocketAccessService accessService;
    UUID roomId;

    @BeforeEach
    void setUp() {
        accessService = new CommunityWebSocketAccessService(quotaService, roomMemberRepository);
        roomId = UUID.randomUUID();
    }

    @Test
    void validateRoomSubscriptionAllowsActiveMemberWithChatFeature() {
        when(roomMemberRepository.existsActiveMembership(
                roomId,
                "user-1",
                CommunityRoomMemberStatus.ACTIVE,
                CommunityRoomStatus.ACTIVE
        )).thenReturn(true);

        assertDoesNotThrow(() -> accessService.validateRoomSubscription("user-1", roomId));
        verify(quotaService).validateFeature("user-1", PlanFeatureKeys.COMMUNITY_CHAT);
    }

    @Test
    void validateRoomSubscriptionRejectsNonMember() {
        when(roomMemberRepository.existsActiveMembership(
                roomId,
                "user-1",
                CommunityRoomMemberStatus.ACTIVE,
                CommunityRoomStatus.ACTIVE
        )).thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> accessService.validateRoomSubscription("user-1", roomId)
        );

        assertEquals(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND, exception.getErrorCode());
    }
}
