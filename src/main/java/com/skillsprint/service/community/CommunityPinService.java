package com.skillsprint.service.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.community.CreateCommunityPinRequest;
import com.skillsprint.dto.request.community.ReorderCommunityPinsRequest;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.dto.response.community.CommunityPinResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.CommunityChatMessage;
import com.skillsprint.entity.CommunityPinnedItem;
import com.skillsprint.entity.CommunityRoom;
import com.skillsprint.entity.CommunityRoomMember;
import com.skillsprint.entity.User;
import com.skillsprint.enums.community.CommunityPinItemType;
import com.skillsprint.enums.community.CommunityRoomMemberStatus;
import com.skillsprint.enums.community.CommunityRoomRole;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.CommunityChatMessageRepository;
import com.skillsprint.repository.CommunityPinnedItemRepository;
import com.skillsprint.repository.CommunityRoomMemberRepository;
import com.skillsprint.repository.CommunityRoomRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityPinService {

    CommunityPinnedItemRepository pinRepository;
    CommunityRoomRepository roomRepository;
    CommunityRoomMemberRepository memberRepository;
    CommunityChatMessageRepository messageRepository;
    UserRepository userRepository;
    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;
    QuotaService quotaService;

    @Transactional(readOnly = true)
    public List<CommunityPinResponse> getPins(String userId, UUID roomId) {
        validateCommunityPin(userId);
        requireMember(roomId, userId);

        return pinRepository.findByRoomRoomIdOrderByDisplayOrderAscCreatedAtDesc(roomId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommunityPinResponse createPin(String userId, UUID roomId, CreateCommunityPinRequest request) {
        validateCommunityPin(userId);
        User user = findUser(userId);
        CommunityRoom room = findRoom(roomId);
        requireModerator(roomId, userId);

        CommunityPinnedItem pin = new CommunityPinnedItem();
        pin.setRoom(room);
        pin.setPinnedBy(user);
        pin.setItemType(request.getItemType());
        pin.setTitle(normalizeRequired(request.getTitle(), ErrorCode.COMMUNITY_PIN_TITLE_REQUIRED));
        pin.setDisplayOrder((int) pinRepository.countByRoomRoomId(roomId));

        if (request.getItemType() == CommunityPinItemType.MESSAGE) {
            CommunityChatMessage message = resolveMessage(roomId, request.getMessageId());
            pin.setMessage(message);
            pin.setContent(message.getMaskedContent());
        } else {
            pin.setContent(normalizeRequired(request.getContent(), ErrorCode.COMMUNITY_PIN_CONTENT_REQUIRED));
        }

        CommunityPinnedItem saved = pinRepository.save(pin);
        logActivity(
                userId,
                saved,
                BusinessActionType.COMMUNITY_PIN_CREATED,
                "Tạo pin trong phòng cộng đồng",
                null,
                pinSnapshot(saved)
        );
        return toResponse(saved);
    }

    @Transactional
    public void deletePin(String actorUserId, UUID roomId, UUID pinId, boolean admin) {
        CommunityPinnedItem pin = findPinInRoom(roomId, pinId);
        if (!admin) {
            validateCommunityPin(actorUserId);
            requireModerator(roomId, actorUserId);
        }

        Map<String, Object> oldValue = pinSnapshot(pin);
        pinRepository.delete(pin);
        logActivity(
                actorUserId,
                pin,
                BusinessActionType.COMMUNITY_PIN_DELETED,
                admin ? "Admin xóa pin trong phòng cộng đồng" : "Xóa pin trong phòng cộng đồng",
                oldValue,
                null
        );
    }

    @Transactional
    public List<CommunityPinResponse> reorderPins(String userId, UUID roomId, ReorderCommunityPinsRequest request) {
        validateCommunityPin(userId);
        requireModerator(roomId, userId);

        List<CommunityPinnedItem> pins = pinRepository.findByRoomRoomIdOrderByDisplayOrderAscCreatedAtDesc(roomId);
        Map<UUID, CommunityPinnedItem> byId = new LinkedHashMap<>();
        pins.forEach(pin -> byId.put(pin.getPinId(), pin));

        int order = 0;
        for (UUID pinId : request.getPinIds()) {
            CommunityPinnedItem pin = byId.get(pinId);
            if (pin != null) {
                pin.setDisplayOrder(order++);
            }
        }
        for (CommunityPinnedItem pin : pins) {
            if (!request.getPinIds().contains(pin.getPinId())) {
                pin.setDisplayOrder(order++);
            }
        }

        List<CommunityPinnedItem> savedPins = pinRepository.saveAll(pins);
        logActivity(
                userId,
                null,
                BusinessActionType.COMMUNITY_PIN_REORDERED,
                "Sắp xếp pin trong phòng cộng đồng",
                null,
                Map.of("roomId", roomId, "pinIds", request.getPinIds())
        );

        return savedPins.stream()
                .sorted((left, right) -> Integer.compare(left.getDisplayOrder(), right.getDisplayOrder()))
                .map(this::toResponse)
                .toList();
    }

    private CommunityChatMessage resolveMessage(UUID roomId, UUID messageId) {
        if (messageId == null) {
            throw new AppException(ErrorCode.COMMUNITY_PIN_MESSAGE_REQUIRED);
        }

        CommunityChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_NOT_FOUND));
        if (!message.getRoom().getRoomId().equals(roomId)) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_NOT_FOUND);
        }
        if (message.isHidden()) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_NOT_FOUND);
        }
        return message;
    }

    private CommunityPinnedItem findPinInRoom(UUID roomId, UUID pinId) {
        CommunityPinnedItem pin = pinRepository.findById(pinId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_PIN_NOT_FOUND));
        if (!pin.getRoom().getRoomId().equals(roomId)) {
            throw new AppException(ErrorCode.COMMUNITY_PIN_NOT_FOUND);
        }
        return pin;
    }

    private CommunityRoom findRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_NOT_FOUND));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateCommunityPin(String userId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.COMMUNITY_PIN);
    }

    private CommunityRoomMember requireMember(UUID roomId, String userId) {
        CommunityRoomMember member = memberRepository.findByRoomRoomIdAndUserUserId(roomId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND));
        if (member.isBanned()) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
        }
        if (member.getStatus() != null && member.getStatus() != CommunityRoomMemberStatus.ACTIVE) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private void requireModerator(UUID roomId, String userId) {
        CommunityRoomMember member = requireMember(roomId, userId);
        if (member.getRole() != CommunityRoomRole.OWNER && member.getRole() != CommunityRoomRole.MODERATOR) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MODERATOR_REQUIRED);
        }
    }

    private String normalizeRequired(String value, ErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new AppException(errorCode);
        }
        return value.trim();
    }

    private CommunityPinResponse toResponse(CommunityPinnedItem pin) {
        return CommunityPinResponse.builder()
                .pinId(pin.getPinId())
                .roomId(pin.getRoom().getRoomId())
                .itemType(pin.getItemType())
                .title(pin.getTitle())
                .content(pin.getContent())
                .messageId(pin.getMessage() != null ? pin.getMessage().getMessageId() : null)
                .pinnedBy(toAuthor(pin.getPinnedBy()))
                .displayOrder(pin.getDisplayOrder())
                .createdAt(pin.getCreatedAt())
                .updatedAt(pin.getUpdatedAt())
                .build();
    }

    private CommunityAuthorResponse toAuthor(User user) {
        if (user == null) {
            return null;
        }
        return CommunityAuthorResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarObjectKey(user.getAvatarObjectKey())
                .build();
    }

    private void logActivity(
            String actorUserId,
            CommunityPinnedItem pin,
            BusinessActionType actionType,
            String title,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (actorUserId != null && !actorUserId.isBlank()) {
            userRepository.findById(actorUserId).ifPresent(log::setUser);
        }
        log.setEntityType(BusinessEntityType.COMMUNITY_PIN);
        log.setEntityId(pin != null ? pin.getPinId() : null);
        log.setActionType(actionType);
        log.setTitle(title);
        log.setDescription("Cập nhật pin trong phòng community");
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));
        activityLogRepository.save(log);
    }

    private Map<String, Object> pinSnapshot(CommunityPinnedItem pin) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pinId", pin.getPinId());
        snapshot.put("roomId", pin.getRoom().getRoomId());
        snapshot.put("itemType", pin.getItemType());
        snapshot.put("title", pin.getTitle());
        snapshot.put("content", pin.getContent());
        snapshot.put("displayOrder", pin.getDisplayOrder());
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
