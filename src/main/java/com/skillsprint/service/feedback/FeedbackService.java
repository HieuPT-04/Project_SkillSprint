package com.skillsprint.service.feedback;

import com.skillsprint.dto.request.feedback.CreateFeedbackRequest;
import com.skillsprint.dto.request.feedback.ReplyFeedbackRequest;
import com.skillsprint.dto.request.feedback.UpdateFeedbackStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.feedback.FeedbackAdminResponse;
import com.skillsprint.dto.response.feedback.FeedbackResponse;
import com.skillsprint.dto.response.feedback.FeedbackSubmitResponse;
import com.skillsprint.entity.Feedback;
import com.skillsprint.entity.User;
import com.skillsprint.enums.feedback.FeedbackStatus;
import com.skillsprint.enums.feedback.FeedbackType;
import com.skillsprint.enums.notification.NotificationType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.FeedbackRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.notification.NotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedbackService {

    static int MAX_PAGE_SIZE = 100;

    FeedbackRepository feedbackRepository;
    UserRepository userRepository;
    NotificationService notificationService;

    @Transactional
    public FeedbackSubmitResponse createFeedback(String userId, CreateFeedbackRequest request) {
        User user = findUser(userId);

        Feedback feedback = new Feedback();
        feedback.setUser(user);
        feedback.setType(request.getType());
        feedback.setTitle(request.getTitle().trim());
        feedback.setContent(request.getContent().trim());
        feedback.setRelatedUrl(normalizeBlank(request.getRelatedUrl()));
        feedback.setStatus(FeedbackStatus.OPEN);

        return toSubmitResponse(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getMyFeedback(String userId) {
        findUser(userId);

        return feedbackRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toFeedbackResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getMyFeedbackDetail(String userId, UUID feedbackId) {
        findUser(userId);

        Feedback feedback = feedbackRepository
                .findByFeedbackIdAndUserUserId(feedbackId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FEEDBACK_NOT_FOUND));

        return toFeedbackResponse(feedback);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedbackAdminResponse> getAdminFeedback(
            FeedbackType type,
            FeedbackStatus status,
            String search,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "created_at")
        );

        Page<FeedbackAdminResponse> feedback = feedbackRepository
                .searchAdminFeedback(
                        type != null ? type.name() : null,
                        status != null ? status.name() : null,
                        toSearchPattern(search),
                        pageable
                )
                .map(this::toAdminResponse);

        return PageResponse.from(feedback);
    }

    @Transactional(readOnly = true)
    public FeedbackAdminResponse getFeedback(UUID feedbackId) {
        return toAdminResponse(findFeedback(feedbackId));
    }

    @Transactional
    public FeedbackAdminResponse updateFeedbackStatus(UUID feedbackId, UpdateFeedbackStatusRequest request) {
        Feedback feedback = findFeedback(feedbackId);
        feedback.setStatus(request.getStatus());
        feedback.setAdminNote(normalizeBlank(request.getAdminNote()));

        return toAdminResponse(feedbackRepository.save(feedback));
    }

    @Transactional
    public FeedbackAdminResponse replyFeedback(String adminUserId, UUID feedbackId, ReplyFeedbackRequest request) {
        Feedback feedback = findFeedback(feedbackId);
        User admin = findUser(adminUserId);

        feedback.setAdminReply(request.getMessage().trim());
        feedback.setRepliedBy(admin);
        feedback.setRepliedAt(java.time.Instant.now());

        Feedback savedFeedback = feedbackRepository.save(feedback);
        notifyFeedbackReply(savedFeedback);

        return toAdminResponse(savedFeedback);
    }

    @Transactional
    public void deleteFeedback(UUID feedbackId) {
        Feedback feedback = findFeedback(feedbackId);
        feedbackRepository.delete(feedback);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Feedback findFeedback(UUID feedbackId) {
        return feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new AppException(ErrorCode.FEEDBACK_NOT_FOUND));
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeSearch(String search) {
        return normalizeBlank(search);
    }

    private String toSearchPattern(String search) {
        String normalized = normalizeSearch(search);
        return normalized != null ? "%" + normalized + "%" : null;
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private FeedbackSubmitResponse toSubmitResponse(Feedback feedback) {
        return FeedbackSubmitResponse.builder()
                .feedbackId(feedback.getFeedbackId())
                .type(feedback.getType())
                .title(feedback.getTitle())
                .status(feedback.getStatus())
                .createdAt(feedback.getCreatedAt())
                .build();
    }

    private FeedbackResponse toFeedbackResponse(Feedback feedback) {
        return FeedbackResponse.builder()
                .feedbackId(feedback.getFeedbackId())
                .type(feedback.getType())
                .title(feedback.getTitle())
                .content(feedback.getContent())
                .relatedUrl(feedback.getRelatedUrl())
                .status(feedback.getStatus())
                .adminReply(feedback.getAdminReply())
                .repliedAt(feedback.getRepliedAt())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }

    private FeedbackAdminResponse toAdminResponse(Feedback feedback) {
        User user = feedback.getUser();
        User repliedBy = feedback.getRepliedBy();

        return FeedbackAdminResponse.builder()
                .feedbackId(feedback.getFeedbackId())
                .userId(user != null ? user.getUserId() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .userFullName(user != null ? user.getFullName() : null)
                .type(feedback.getType())
                .title(feedback.getTitle())
                .content(feedback.getContent())
                .relatedUrl(feedback.getRelatedUrl())
                .status(feedback.getStatus())
                .adminNote(feedback.getAdminNote())
                .adminReply(feedback.getAdminReply())
                .repliedByUserId(repliedBy != null ? repliedBy.getUserId() : null)
                .repliedByFullName(repliedBy != null ? repliedBy.getFullName() : null)
                .repliedAt(feedback.getRepliedAt())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }

    private void notifyFeedbackReply(Feedback feedback) {
        User user = feedback.getUser();
        if (user == null) {
            return;
        }

        notificationService.createAndDispatch(
                user,
                null,
                NotificationType.FEEDBACK_REPLIED,
                "Feedback của bạn đã được phản hồi",
                "Admin đã phản hồi feedback \"%s\".".formatted(feedback.getTitle())
        );
    }
}
