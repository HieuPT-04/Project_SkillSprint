package com.skillsprint.service.community;

import com.skillsprint.enums.community.CommunityRoomMemberStatus;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CommunityRoomMemberRepository;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityWebSocketAccessService {

    QuotaService quotaService;
    CommunityRoomMemberRepository roomMemberRepository;

    @Transactional(readOnly = true)
    public void validateRoomSubscription(String userId, UUID roomId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.COMMUNITY_CHAT);
        boolean activeMember = roomMemberRepository.existsActiveMembership(
                roomId,
                userId,
                CommunityRoomMemberStatus.ACTIVE,
                CommunityRoomStatus.ACTIVE
        );
        if (!activeMember) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND);
        }
    }
}
