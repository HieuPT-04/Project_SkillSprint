package com.skillsprint.service.community;

import com.skillsprint.dto.request.community.CreateContentReportRequest;
import com.skillsprint.dto.request.community.HideCommunityChatMessageRequest;
import com.skillsprint.dto.request.community.SendCommunityChatMessageRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.dto.response.community.CommunityChatMessageResponse;
import com.skillsprint.dto.response.community.ContentReportResponse;
import com.skillsprint.entity.CommunityChatMessage;
import com.skillsprint.entity.CommunityRoom;
import com.skillsprint.entity.CommunityRoomMember;
import com.skillsprint.entity.ContentReport;
import com.skillsprint.entity.User;
import com.skillsprint.enums.community.CommunityRoomStatus;
import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CommunityChatMessageRepository;
import com.skillsprint.repository.CommunityRoomMemberRepository;
import com.skillsprint.repository.CommunityRoomRepository;
import com.skillsprint.repository.ContentReportRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.ratelimit.RateLimitService;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import java.time.Instant;
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
public class CommunityChatService {

    static int MAX_PAGE_SIZE = 100;
    static int MAX_MESSAGE_LENGTH = 2000;
    static int MESSAGE_AUTO_HIDE_REPORT_THRESHOLD = 3;
    static String MASKED_CONTENT = "[Nội dung đã được ẩn do chứa từ khóa không phù hợp]";

    CommunityChatMessageRepository messageRepository;
    CommunityRoomRepository roomRepository;
    CommunityRoomMemberRepository memberRepository;
    ContentReportRepository reportRepository;
    UserRepository userRepository;
    CommunityBlacklistService blacklistService;
    RateLimitService rateLimitService;
    QuotaService quotaService;

    @Transactional
    public CommunityChatMessageResponse sendMessage(
            String userId,
            UUID roomId,
            SendCommunityChatMessageRequest request
    ) {
        validateCommunityChat(userId);
        User sender = findUser(userId);
        CommunityRoom room = findActiveRoom(roomId);
        CommunityRoomMember member = requireActiveMember(roomId, userId);
        requireNotMuted(member);
        rateLimitService.checkCommunityChat(userId, roomId.toString());

        String content = normalizeMessage(request.getContent());
        CommunityChatMessage message = new CommunityChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setRawContent(content);
        message.setMaskedContent(maskContent(content));

        return toUserResponse(messageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityChatMessageResponse> getHistory(
            String userId,
            UUID roomId,
            int page,
            int size
    ) {
        validateCommunityChat(userId);
        findActiveRoom(roomId);
        requireActiveMember(roomId, userId);

        Pageable pageable = pageable(page, size);
        Page<CommunityChatMessageResponse> messages = messageRepository
                .findByRoomRoomIdAndHiddenFalse(roomId, pageable)
                .map(this::toUserResponse);

        return PageResponse.from(messages);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityChatMessageResponse> getAdminMessages(
            UUID roomId,
            int page,
            int size
    ) {
        findRoom(roomId);

        Page<CommunityChatMessageResponse> messages = messageRepository
                .findByRoomRoomId(roomId, pageable(page, size))
                .map(this::toAdminResponse);

        return PageResponse.from(messages);
    }

    @Transactional
    public CommunityChatMessageResponse hideMessage(
            String actorUserId,
            UUID roomId,
            UUID messageId,
            HideCommunityChatMessageRequest request,
            boolean admin
    ) {
        if (!admin) {
            validateCommunityChat(actorUserId);
        }
        CommunityChatMessage message = findMessageInRoom(roomId, messageId);
        if (!admin) {
            requireModerator(roomId, actorUserId);
        }

        message.setHidden(request.getHidden() == null || request.getHidden());
        message.setAdminNote(normalizeBlank(request.getAdminNote()));

        CommunityChatMessage saved = messageRepository.save(message);
        return admin ? toAdminResponse(saved) : toUserResponse(saved);
    }

    @Transactional
    public ContentReportResponse reportMessage(
            String userId,
            UUID roomId,
            UUID messageId,
            CreateContentReportRequest request
    ) {
        validateCommunityChat(userId);
        findActiveRoom(roomId);
        requireActiveMember(roomId, userId);
        CommunityChatMessage message = findMessageInRoom(roomId, messageId);
        if (message.isHidden()) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_NOT_FOUND);
        }

        User reporter = findUser(userId);
        reportRepository.findByTargetTypeAndTargetIdAndReporterUserId(
                ContentReportTargetType.MESSAGE,
                messageId,
                userId
        ).ifPresent(report -> {
            throw new AppException(ErrorCode.COMMUNITY_REPORT_DUPLICATED);
        });

        ContentReport report = new ContentReport();
        report.setTargetType(ContentReportTargetType.MESSAGE);
        report.setTargetId(messageId);
        report.setReporter(reporter);
        report.setReason(normalizeReportReason(request.getReason()));

        ContentReport saved = reportRepository.save(report);
        applyMessageReportThreshold(message);
        return toReportResponse(saved);
    }

    private void applyMessageReportThreshold(CommunityChatMessage message) {
        long pendingReports = reportRepository.countByTargetTypeAndTargetIdAndStatus(
                ContentReportTargetType.MESSAGE,
                message.getMessageId(),
                ContentReportStatus.PENDING
        );
        message.setReportCount((int) pendingReports);
        if (pendingReports >= MESSAGE_AUTO_HIDE_REPORT_THRESHOLD) {
            message.setHidden(true);
            message.setAdminNote("Tự ẩn do vượt ngưỡng report");
        }
        messageRepository.save(message);
    }

    private CommunityRoom findActiveRoom(UUID roomId) {
        CommunityRoom room = findRoom(roomId);
        if (room.getStatus() != CommunityRoomStatus.ACTIVE) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_NOT_ACTIVE);
        }
        return room;
    }

    private CommunityRoom findRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_NOT_FOUND));
    }

    private CommunityChatMessage findMessageInRoom(UUID roomId, UUID messageId) {
        CommunityChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_NOT_FOUND));
        if (!message.getRoom().getRoomId().equals(roomId)) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_NOT_FOUND);
        }
        return message;
    }

    private CommunityRoomMember requireActiveMember(UUID roomId, String userId) {
        CommunityRoomMember member = memberRepository.findByRoomRoomIdAndUserUserId(roomId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_NOT_FOUND));
        if (member.isBanned()) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_MEMBER_BANNED);
        }
        return member;
    }

    private void requireModerator(UUID roomId, String userId) {
        CommunityRoomMember member = requireActiveMember(roomId, userId);
        switch (member.getRole()) {
            case OWNER, MODERATOR -> {
            }
            default -> throw new AppException(ErrorCode.COMMUNITY_ROOM_MODERATOR_REQUIRED);
        }
    }

    private void requireNotMuted(CommunityRoomMember member) {
        if (member.getMuteUntil() != null && member.getMuteUntil().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MEMBER_MUTED);
        }
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateCommunityChat(String userId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.COMMUNITY_CHAT);
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "sentAt")
        );
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 30;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeMessage(String content) {
        String normalized = normalizeBlank(content);
        if (normalized == null) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_REQUIRED);
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_MESSAGE_TOO_LONG);
        }
        return normalized;
    }

    private String normalizeReportReason(String reason) {
        String normalized = normalizeBlank(reason);
        return normalized == null ? "Người dùng báo cáo tin nhắn không phù hợp" : normalized;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String maskContent(String content) {
        return blacklistService.containsBadWords(content) ? MASKED_CONTENT : content;
    }

    private CommunityChatMessageResponse toUserResponse(CommunityChatMessage message) {
        return CommunityChatMessageResponse.builder()
                .messageId(message.getMessageId())
                .roomId(message.getRoom().getRoomId())
                .sender(toAuthor(message.getSender()))
                .content(message.getMaskedContent())
                .hidden(message.isHidden())
                .sentAt(message.getSentAt())
                .build();
    }

    private CommunityChatMessageResponse toAdminResponse(CommunityChatMessage message) {
        return CommunityChatMessageResponse.builder()
                .messageId(message.getMessageId())
                .roomId(message.getRoom().getRoomId())
                .sender(toAuthor(message.getSender()))
                .content(message.getMaskedContent())
                .rawContent(message.getRawContent())
                .hidden(message.isHidden())
                .reportCount(message.getReportCount())
                .adminNote(message.getAdminNote())
                .sentAt(message.getSentAt())
                .build();
    }

    private ContentReportResponse toReportResponse(ContentReport report) {
        return ContentReportResponse.builder()
                .reportId(report.getReportId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reporter(toAuthor(report.getReporter()))
                .reason(report.getReason())
                .status(report.getStatus())
                .adminNote(report.getAdminNote())
                .reviewedAt(report.getReviewedAt())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
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
}
