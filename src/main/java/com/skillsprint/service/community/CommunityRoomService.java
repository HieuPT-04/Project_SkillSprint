package com.skillsprint.service.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.community.CreateCommunityRoomInviteRequest;
import com.skillsprint.dto.request.community.CreateCommunityRoomRequest;
import com.skillsprint.dto.request.community.MuteCommunityRoomMemberRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomMemberRoleRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomRequest;
import com.skillsprint.dto.request.community.UpdateCommunityRoomStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.dto.response.community.CommunityRoomInviteResponse;
import com.skillsprint.dto.response.community.CommunityRoomMemberResponse;
import com.skillsprint.dto.response.community.CommunityRoomResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.CommunityRoom;
import com.skillsprint.entity.CommunityRoomInvite;
import com.skillsprint.entity.CommunityRoomMember;
import com.skillsprint.entity.User;
import com.skillsprint.enums.community.CommunityRoomInviteStatus;
import com.skillsprint.enums.community.CommunityRoomMemberStatus;
import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomRole;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.CommunityRoomInviteRepository;
import com.skillsprint.repository.CommunityRoomMemberRepository;
import com.skillsprint.repository.CommunityRoomRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityRoomService {

    static int MAX_PAGE_SIZE = 100;
    static int DEFAULT_INVITE_HOURS = 48;

    CommunityRoomRepository roomRepository;
    CommunityRoomMemberRepository memberRepository;
    CommunityRoomInviteRepository inviteRepository;
    UserRepository userRepository;
    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;
    CommunityBlacklistService blacklistService;
    QuotaService quotaService;
    S3PresignedUrlService s3PresignedUrlService;

    @Transactional
    public CommunityRoomResponse createRoom(String userId, CreateCommunityRoomRequest request) {
        quotaService.validateCanCreateCommunityRoom(userId);
        User owner = findUser(userId);
        String name = normalizeRequiredName(request.getName());
        String description = normalizeBlank(request.getDescription());

        CommunityRoom room = new CommunityRoom();
        room.setName(name);
        room.setDescription(description);
        room.setMode(request.getMode() != null ? request.getMode() : CommunityRoomMode.PUBLIC);
        room.setMaxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : 50);
        room.setOwner(owner);
        room.setStatus(resolveRoomStatus(name, description));

        CommunityRoom savedRoom = roomRepository.save(room);
        CommunityRoomMember ownerMember = createMember(savedRoom, owner, CommunityRoomRole.OWNER);
        memberRepository.save(ownerMember);
        savedRoom.setMemberCount(1);
        savedRoom = roomRepository.save(savedRoom);

        logActivity(
                userId,
                BusinessEntityType.COMMUNITY_ROOM,
                savedRoom.getRoomId(),
                BusinessActionType.COMMUNITY_ROOM_CREATED,
                "Tạo phòng cộng đồng",
                "User tạo phòng community",
                null,
                roomSnapshot(savedRoom)
        );

        return toRoomResponse(savedRoom, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityRoomResponse> discoverRooms(
            String userId,
            CommunityRoomMode mode,
            String search,
            int page,
            int size
    ) {
        validateCommunityRoom(userId);
        Pageable pageable = pageable(page, size);
        Page<CommunityRoom> rooms = roomRepository
                .searchDiscoverableRooms(mode, normalizeBlank(search), pageable);

        return toRoomPage(rooms, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityRoomResponse> getMyRooms(String userId, int page, int size) {
        validateCommunityRoom(userId);
        findUser(userId);

        Pageable pageable = pageable(page, size);
        Page<CommunityRoomMember> memberships = memberRepository
                .findActiveByUserId(userId, CommunityRoomMemberStatus.ACTIVE, pageable);

        List<CommunityRoomResponse> items = memberships.getContent().stream()
                .map(member -> toRoomResponse(member.getRoom(), member))
                .toList();
        return toPageResponse(memberships, items);
    }

    @Transactional(readOnly = true)
    public CommunityRoomResponse getRoom(String userId, UUID roomId) {
        validateCommunityRoom(userId);
        CommunityRoom room = findRoom(roomId);
        CommunityRoomMember member = memberRepository.findByRoomRoomIdAndUserUserId(roomId, userId).orElse(null);
        boolean joined = isActive(member);
        boolean visible = room.getStatus() == CommunityRoomStatus.ACTIVE
                && room.getMode() != CommunityRoomMode.PRIVATE;
        if (!joined && !visible) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_NOT_FOUND);
        }

        return toRoomResponse(room, userId);
    }

    @Transactional
    public CommunityRoomResponse updateRoom(String userId, UUID roomId, UpdateCommunityRoomRequest request) {
        validateCommunityRoom(userId);
        CommunityRoom room = findRoom(roomId);
        requireOwner(roomId, userId);

        Map<String, Object> oldValue = roomSnapshot(room);
        if (request.getName() != null) {
            room.setName(normalizeRequiredName(request.getName()));
        }
        if (request.getDescription() != null) {
            room.setDescription(normalizeBlank(request.getDescription()));
        }
        if (request.getMode() != null) {
            room.setMode(request.getMode());
        }
        if (request.getMaxMembers() != null) {
            room.setMaxMembers(request.getMaxMembers());
        }
        if (blacklistService.containsBadWords(room.getName())
                || blacklistService.containsBadWords(room.getDescription())) {
            room.setStatus(CommunityRoomStatus.HIDDEN);
            room.setAdminNote("Tự ẩn do tên hoặc mô tả phòng chứa blacklist");
        }

        CommunityRoom saved = roomRepository.save(room);
        logActivity(
                userId,
                BusinessEntityType.COMMUNITY_ROOM,
                saved.getRoomId(),
                BusinessActionType.COMMUNITY_ROOM_UPDATED,
                "Cập nhật phòng cộng đồng",
                "Owner cập nhật thông tin phòng community",
                oldValue,
                roomSnapshot(saved)
        );

        return toRoomResponse(saved, userId);
    }

    @Transactional
    public void deleteRoom(String userId, UUID roomId) {
        validateCommunityRoom(userId);
        CommunityRoom room = findRoom(roomId);
        requireOwner(roomId, userId);

        room.setStatus(CommunityRoomStatus.DELETED);
        roomRepository.save(room);
    }

    @Transactional
    public CommunityRoomResponse joinRoom(String userId, UUID roomId) {
        validateCommunityRoom(userId);
        User user = findUser(userId);
        CommunityRoom room = findRoom(roomId);
        if (room.getStatus() != CommunityRoomStatus.ACTIVE) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_NOT_ACTIVE);
        }
        if (room.getMode() != CommunityRoomMode.PUBLIC) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MODERATOR_REQUIRED);
        }

        CommunityRoomMember existing = memberRepository.findByRoomRoomIdAndUserUserId(roomId, userId).orElse(null);
        if (existing != null) {
            if (isBanned(existing)) {
                throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
            }
            if (isActive(existing)) {
                throw new AppException(ErrorCode.COMMUNITY_ROOM_ALREADY_JOINED);
            }
            activateMembership(existing, CommunityRoomRole.MEMBER);
            memberRepository.saveAndFlush(existing);
        } else {
            memberRepository.saveAndFlush(createMember(room, user, CommunityRoomRole.MEMBER));
        }

        roomRepository.adjustMemberCount(roomId, 1);
        room = findRoom(roomId);

        return toRoomResponse(room, userId);
    }

    @Transactional
    public void leaveRoom(String userId, UUID roomId) {
        validateCommunityRoom(userId);
        CommunityRoomMember member = findMembership(roomId, userId);
        requireActive(member);
        if (member.getRole() == CommunityRoomRole.OWNER) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_OWNER_CANNOT_LEAVE);
        }

        member.setStatus(CommunityRoomMemberStatus.LEFT);
        member.setLeftAt(Instant.now());
        member.setRemovedAt(null);
        member.setRemovedBy(null);
        member.setRemovalReason("User tự rời phòng");
        member.setMuteUntil(null);
        memberRepository.saveAndFlush(member);
        roomRepository.adjustMemberCount(roomId, -1);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityRoomMemberResponse> getMembers(
            String userId,
            UUID roomId,
            int page,
            int size
    ) {
        validateCommunityRoom(userId);
        requireMember(roomId, userId);
        Page<CommunityRoomMemberResponse> members = memberRepository
                .findActiveByRoomId(roomId, CommunityRoomMemberStatus.ACTIVE, pageable(page, size))
                .map(this::toMemberResponse);

        return PageResponse.from(members);
    }

    @Transactional
    public CommunityRoomInviteResponse inviteMember(
            String inviterUserId,
            UUID roomId,
            CreateCommunityRoomInviteRequest request
    ) {
        validateCommunityRoom(inviterUserId);
        User inviter = findUser(inviterUserId);
        User invitee = findUser(request.getInviteeUserId());
        CommunityRoom room = findRoom(roomId);
        requireModerator(roomId, inviterUserId);

        CommunityRoomMember existingMember = memberRepository
                .findByRoomRoomIdAndUserUserId(roomId, invitee.getUserId())
                .orElse(null);
        if (existingMember != null && isBanned(existingMember)) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
        }
        if (existingMember != null && isActive(existingMember)) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_ALREADY_JOINED);
        }
        inviteRepository.findByRoomRoomIdAndInviteeUserIdAndStatus(
                roomId,
                invitee.getUserId(),
                CommunityRoomInviteStatus.PENDING
        ).ifPresent(invite -> {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_INVITE_DUPLICATED);
        });

        CommunityRoomInvite invite = new CommunityRoomInvite();
        invite.setRoom(room);
        invite.setInviter(inviter);
        invite.setInvitee(invitee);
        invite.setStatus(CommunityRoomInviteStatus.PENDING);
        invite.setExpiresAt(Instant.now().plus(DEFAULT_INVITE_HOURS, ChronoUnit.HOURS));

        return toInviteResponse(inviteRepository.save(invite));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityRoomInviteResponse> getMyInvites(String userId, int page, int size) {
        validateCommunityRoom(userId);
        findUser(userId);

        Page<CommunityRoomInviteResponse> invites = inviteRepository
                .findByInviteeUserIdAndStatusAndExpiresAtAfter(
                        userId,
                        CommunityRoomInviteStatus.PENDING,
                        Instant.now(),
                        pageable(page, size)
                )
                .map(this::toInviteResponse);

        return PageResponse.from(invites);
    }

    @Transactional
    public CommunityRoomResponse acceptInvite(String userId, UUID inviteId) {
        validateCommunityRoom(userId);
        CommunityRoomInvite invite = findInvite(inviteId);
        requireInvitee(invite, userId);
        requirePendingInvite(invite);

        CommunityRoom room = invite.getRoom();
        if (room.getStatus() != CommunityRoomStatus.ACTIVE) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_NOT_ACTIVE);
        }

        CommunityRoomMember existing = memberRepository
                .findByRoomRoomIdAndUserUserId(room.getRoomId(), userId)
                .orElse(null);
        if (existing != null && isBanned(existing)) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
        }
        if (existing == null || !isActive(existing)) {
            if (existing == null) {
                memberRepository.saveAndFlush(createMember(room, invite.getInvitee(), CommunityRoomRole.MEMBER));
            } else {
                activateMembership(existing, CommunityRoomRole.MEMBER);
                memberRepository.saveAndFlush(existing);
            }
            roomRepository.adjustMemberCount(room.getRoomId(), 1);
            room = findRoom(room.getRoomId());
        }

        invite.setStatus(CommunityRoomInviteStatus.ACCEPTED);
        inviteRepository.save(invite);
        return toRoomResponse(room, userId);
    }

    @Transactional
    public CommunityRoomInviteResponse declineInvite(String userId, UUID inviteId) {
        validateCommunityRoom(userId);
        CommunityRoomInvite invite = findInvite(inviteId);
        requireInvitee(invite, userId);
        requirePendingInvite(invite);

        invite.setStatus(CommunityRoomInviteStatus.DECLINED);
        return toInviteResponse(inviteRepository.save(invite));
    }

    @Transactional
    public CommunityRoomMemberResponse updateMemberRole(
            String actorUserId,
            UUID roomId,
            String targetUserId,
            UpdateCommunityRoomMemberRoleRequest request
    ) {
        validateCommunityRoom(actorUserId);
        requireOwner(roomId, actorUserId);
        CommunityRoomMember member = findMembership(roomId, targetUserId);
        requireActive(member);
        if (member.getRole() == CommunityRoomRole.OWNER || request.getRole() == CommunityRoomRole.OWNER) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_OWNER_ACTION_NOT_ALLOWED);
        }

        member.setRole(request.getRole());
        CommunityRoomMember saved = memberRepository.save(member);
        logMemberActivity(actorUserId, saved, BusinessActionType.COMMUNITY_ROOM_MEMBER_ROLE_UPDATED);
        return toMemberResponse(saved);
    }

    @Transactional
    public CommunityRoomMemberResponse muteMember(
            String actorUserId,
            UUID roomId,
            String targetUserId,
            MuteCommunityRoomMemberRequest request
    ) {
        validateCommunityRoom(actorUserId);
        requireModerator(roomId, actorUserId);
        CommunityRoomMember member = findMembership(roomId, targetUserId);
        requireActive(member);
        requireNotOwner(member);

        int minutes = request.getMinutes() != null ? request.getMinutes() : 30;
        member.setMuteUntil(Instant.now().plus(minutes, ChronoUnit.MINUTES));
        CommunityRoomMember saved = memberRepository.save(member);
        logMemberActivity(actorUserId, saved, BusinessActionType.COMMUNITY_ROOM_MEMBER_MUTED);
        return toMemberResponse(saved);
    }

    @Transactional
    public void kickMember(String actorUserId, UUID roomId, String targetUserId) {
        validateCommunityRoom(actorUserId);
        requireModerator(roomId, actorUserId);
        CommunityRoomMember member = findMembership(roomId, targetUserId);
        requireActive(member);
        requireNotOwner(member);

        member.setStatus(CommunityRoomMemberStatus.KICKED);
        member.setRemovedAt(Instant.now());
        member.setRemovedBy(findUser(actorUserId));
        member.setRemovalReason("Bị owner hoặc moderator kick khỏi phòng");
        member.setMuteUntil(null);
        CommunityRoomMember saved = memberRepository.saveAndFlush(member);
        roomRepository.adjustMemberCount(roomId, -1);
        logMemberActivity(actorUserId, saved, BusinessActionType.COMMUNITY_ROOM_MEMBER_KICKED);
    }

    @Transactional
    public CommunityRoomMemberResponse banMember(String actorUserId, UUID roomId, String targetUserId) {
        validateCommunityRoom(actorUserId);
        requireModerator(roomId, actorUserId);
        CommunityRoomMember member = findMembership(roomId, targetUserId);
        requireNotOwner(member);

        if (isBanned(member)) {
            return toMemberResponse(member);
        }

        requireActive(member);
        member.setBanned(true);
        member.setStatus(CommunityRoomMemberStatus.BANNED);
        member.setMuteUntil(null);
        member.setRemovedAt(Instant.now());
        member.setRemovedBy(findUser(actorUserId));
        member.setRemovalReason("Bị cấm khỏi phòng");
        memberRepository.saveAndFlush(member);
        roomRepository.adjustMemberCount(roomId, -1);

        CommunityRoomMember saved = memberRepository.findById(member.getMemberId()).orElse(member);
        logMemberActivity(actorUserId, saved, BusinessActionType.COMMUNITY_ROOM_MEMBER_BANNED);
        return toMemberResponse(saved);
    }

    @Transactional
    public CommunityRoomMemberResponse unbanMember(String actorUserId, UUID roomId, String targetUserId) {
        validateCommunityRoom(actorUserId);
        requireModerator(roomId, actorUserId);
        CommunityRoomMember member = memberRepository.findByRoomRoomIdAndUserUserId(roomId, targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND));

        if (isBanned(member)) {
            member.setBanned(false);
            activateMembership(member, CommunityRoomRole.MEMBER);
            memberRepository.saveAndFlush(member);
            roomRepository.adjustMemberCount(roomId, 1);
        }

        CommunityRoomMember saved = memberRepository.findById(member.getMemberId()).orElse(member);
        logMemberActivity(actorUserId, saved, BusinessActionType.COMMUNITY_ROOM_MEMBER_UNBANNED);
        return toMemberResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityRoomResponse> getAdminRooms(
            CommunityRoomStatus status,
            CommunityRoomMode mode,
            String search,
            int page,
            int size
    ) {
        Page<CommunityRoomResponse> rooms = roomRepository
                .searchAdminRooms(status, mode, normalizeBlank(search), pageable(page, size))
                .map(room -> toRoomResponse(room, (CommunityRoomMember) null));

        return PageResponse.from(rooms);
    }

    @Transactional
    public CommunityRoomResponse updateAdminRoomStatus(
            String adminUserId,
            UUID roomId,
            UpdateCommunityRoomStatusRequest request
    ) {
        CommunityRoom room = findRoom(roomId);
        Map<String, Object> oldValue = roomSnapshot(room);
        room.setStatus(request.getStatus());
        room.setAdminNote(normalizeBlank(request.getAdminNote()));

        CommunityRoom saved = roomRepository.save(room);
        logActivity(
                adminUserId,
                BusinessEntityType.COMMUNITY_ROOM,
                saved.getRoomId(),
                BusinessActionType.COMMUNITY_ROOM_STATUS_UPDATED,
                "Cập nhật trạng thái phòng cộng đồng",
                "Admin cập nhật trạng thái phòng community",
                oldValue,
                roomSnapshot(saved)
        );

        return toRoomResponse(saved, (CommunityRoomMember) null);
    }

    private CommunityRoomMember createMember(CommunityRoom room, User user, CommunityRoomRole role) {
        CommunityRoomMember member = new CommunityRoomMember();
        member.setRoom(room);
        member.setUser(user);
        member.setRole(role);
        member.setStatus(CommunityRoomMemberStatus.ACTIVE);
        return member;
    }

    private CommunityRoomStatus resolveRoomStatus(String name, String description) {
        return blacklistService.containsBadWords(name) || blacklistService.containsBadWords(description)
                ? CommunityRoomStatus.HIDDEN
                : CommunityRoomStatus.ACTIVE;
    }

    private void requireMember(UUID roomId, String userId) {
        CommunityRoomMember member = findMembership(roomId, userId);
        if (isBanned(member)) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
        }
        requireActive(member);
    }

    private CommunityRoomMember requireModerator(UUID roomId, String userId) {
        CommunityRoomMember member = findMembership(roomId, userId);
        if (isBanned(member)) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
        }
        requireActive(member);
        if (member.getRole() != CommunityRoomRole.OWNER && member.getRole() != CommunityRoomRole.MODERATOR) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MODERATOR_REQUIRED);
        }
        return member;
    }

    private CommunityRoomMember requireOwner(UUID roomId, String userId) {
        CommunityRoomMember member = findMembership(roomId, userId);
        requireActive(member);
        if (member.getRole() != CommunityRoomRole.OWNER) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_OWNER_REQUIRED);
        }
        return member;
    }

    private void requireNotOwner(CommunityRoomMember member) {
        if (member.getRole() == CommunityRoomRole.OWNER) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_OWNER_ACTION_NOT_ALLOWED);
        }
    }

    private void requireActive(CommunityRoomMember member) {
        if (!isActive(member)) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND);
        }
    }

    private boolean isActive(CommunityRoomMember member) {
        return member != null
                && !member.isBanned()
                && (member.getStatus() == null || member.getStatus() == CommunityRoomMemberStatus.ACTIVE);
    }

    private boolean isBanned(CommunityRoomMember member) {
        return member != null
                && (member.isBanned() || member.getStatus() == CommunityRoomMemberStatus.BANNED);
    }

    private void activateMembership(CommunityRoomMember member, CommunityRoomRole role) {
        member.setRole(role);
        member.setStatus(CommunityRoomMemberStatus.ACTIVE);
        member.setBanned(false);
        member.setMuteUntil(null);
        member.setLeftAt(null);
        member.setRemovedAt(null);
        member.setRemovedBy(null);
        member.setRemovalReason(null);
    }

    private void requireInvitee(CommunityRoomInvite invite, String userId) {
        if (!invite.getInvitee().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.COMMUNITY_ACTION_NOT_ALLOWED);
        }
    }

    private void requirePendingInvite(CommunityRoomInvite invite) {
        if (invite.getStatus() != CommunityRoomInviteStatus.PENDING || invite.getExpiresAt().isBefore(Instant.now())) {
            if (invite.getStatus() == CommunityRoomInviteStatus.PENDING) {
                invite.setStatus(CommunityRoomInviteStatus.EXPIRED);
                inviteRepository.save(invite);
            }
            throw new AppException(ErrorCode.COMMUNITY_ROOM_INVITE_NOT_PENDING);
        }
    }

    private CommunityRoom findRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_NOT_FOUND));
    }

    private CommunityRoomMember findMembership(UUID roomId, String userId) {
        return memberRepository.findByRoomRoomIdAndUserUserId(roomId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND));
    }

    private CommunityRoomInvite findInvite(UUID inviteId) {
        return inviteRepository.findById(inviteId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_INVITE_NOT_FOUND));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateCommunityRoom(String userId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.COMMUNITY_ROOM);
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeRequiredName(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_NAME_REQUIRED);
        }
        return normalized;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CommunityRoomResponse toRoomResponse(CommunityRoom room, String currentUserId) {
        CommunityRoomMember member = currentUserId != null
                ? memberRepository.findByRoomRoomIdAndUserUserId(room.getRoomId(), currentUserId).orElse(null)
                : null;
        return toRoomResponse(room, member);
    }

    private CommunityRoomResponse toRoomResponse(CommunityRoom room, CommunityRoomMember member) {
        boolean activeMember = isActive(member);
        return CommunityRoomResponse.builder()
                .roomId(room.getRoomId())
                .name(room.getName())
                .description(room.getDescription())
                .mode(room.getMode())
                .status(room.getStatus())
                .owner(toAuthor(room.getOwner()))
                .maxMembers(room.getMaxMembers())
                .memberCount(room.getMemberCount())
                .reportCount(room.getReportCount())
                .myRole(activeMember ? member.getRole() : null)
                .joined(activeMember)
                .banned(isBanned(member))
                .adminNote(room.getAdminNote())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    private PageResponse<CommunityRoomResponse> toRoomPage(Page<CommunityRoom> rooms, String currentUserId) {
        Map<UUID, CommunityRoomMember> memberships = findMemberships(rooms.getContent(), currentUserId);
        List<CommunityRoomResponse> items = rooms.getContent().stream()
                .map(room -> toRoomResponse(room, memberships.get(room.getRoomId())))
                .toList();

        return toPageResponse(rooms, items);
    }

    private Map<UUID, CommunityRoomMember> findMemberships(List<CommunityRoom> rooms, String currentUserId) {
        Map<UUID, CommunityRoomMember> memberships = new LinkedHashMap<>();
        if (currentUserId == null || rooms.isEmpty()) {
            return memberships;
        }

        List<UUID> roomIds = rooms.stream()
                .map(CommunityRoom::getRoomId)
                .toList();
        memberRepository.findByRoomRoomIdInAndUserUserId(roomIds, currentUserId)
                .forEach(member -> memberships.put(member.getRoom().getRoomId(), member));
        return memberships;
    }

    private <T> PageResponse<T> toPageResponse(Page<?> page, List<T> items) {
        return PageResponse.<T>builder()
                .items(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private CommunityRoomMemberResponse toMemberResponse(CommunityRoomMember member) {
        return CommunityRoomMemberResponse.builder()
                .memberId(member.getMemberId())
                .roomId(member.getRoom().getRoomId())
                .user(toAuthor(member.getUser()))
                .role(member.getRole())
                .status(member.getStatus() == null ? CommunityRoomMemberStatus.ACTIVE : member.getStatus())
                .muteUntil(member.getMuteUntil())
                .banned(member.isBanned())
                .leftAt(member.getLeftAt())
                .removedAt(member.getRemovedAt())
                .removedBy(toAuthor(member.getRemovedBy()))
                .removalReason(member.getRemovalReason())
                .joinedAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private CommunityRoomInviteResponse toInviteResponse(CommunityRoomInvite invite) {
        return CommunityRoomInviteResponse.builder()
                .inviteId(invite.getInviteId())
                .roomId(invite.getRoom().getRoomId())
                .roomName(invite.getRoom().getName())
                .inviter(toAuthor(invite.getInviter()))
                .invitee(toAuthor(invite.getInvitee()))
                .status(invite.getStatus())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .updatedAt(invite.getUpdatedAt())
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
                .avatarUrl(s3PresignedUrlService.createViewUrl(user.getAvatarObjectKey()))
                .build();
    }

    private void logMemberActivity(
            String actorUserId,
            CommunityRoomMember member,
            BusinessActionType actionType
    ) {
        logActivity(
                actorUserId,
                BusinessEntityType.COMMUNITY_ROOM_MEMBER,
                member.getMemberId(),
                actionType,
                "Cập nhật thành viên phòng cộng đồng",
                "Owner hoặc moderator cập nhật thành viên phòng community",
                null,
                memberSnapshot(member)
        );
    }

    private void logActivity(
            String actorUserId,
            BusinessEntityType entityType,
            UUID entityId,
            BusinessActionType actionType,
            String title,
            String description,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (actorUserId != null && !actorUserId.isBlank()) {
            userRepository.findById(actorUserId).ifPresent(log::setUser);
        }
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActionType(actionType);
        log.setTitle(title);
        log.setDescription(description);
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actorUserId", actorUserId);
        metadata.put("module", "COMMUNITY_ROOM");
        log.setMetadata(toJson(metadata));

        activityLogRepository.save(log);
    }

    private Map<String, Object> roomSnapshot(CommunityRoom room) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roomId", room.getRoomId());
        snapshot.put("name", room.getName());
        snapshot.put("description", room.getDescription());
        snapshot.put("mode", room.getMode());
        snapshot.put("status", room.getStatus());
        snapshot.put("maxMembers", room.getMaxMembers());
        snapshot.put("memberCount", room.getMemberCount());
        snapshot.put("adminNote", room.getAdminNote());
        return snapshot;
    }

    private Map<String, Object> memberSnapshot(CommunityRoomMember member) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("memberId", member.getMemberId());
        snapshot.put("roomId", member.getRoom().getRoomId());
        snapshot.put("userId", member.getUser().getUserId());
        snapshot.put("role", member.getRole());
        snapshot.put("muteUntil", member.getMuteUntil());
        snapshot.put("banned", member.isBanned());
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
