package com.skillsprint.service.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.community.CreateCommunityPostRequest;
import com.skillsprint.dto.request.community.CreateContentReportRequest;
import com.skillsprint.dto.request.community.CreatePostCommentRequest;
import com.skillsprint.dto.request.community.UpdateCommunityPostRequest;
import com.skillsprint.dto.request.community.UpdateCommunityPostStatusRequest;
import com.skillsprint.dto.request.community.UpdateContentReportStatusRequest;
import com.skillsprint.dto.request.community.UpdatePostCommentRequest;
import com.skillsprint.dto.request.community.UpdatePostCommentStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.dto.response.community.CommunityPostResponse;
import com.skillsprint.dto.response.community.CommunityUserPostResponse;
import com.skillsprint.dto.response.community.ContentReportResponse;
import com.skillsprint.dto.response.community.PostCommentResponse;
import com.skillsprint.dto.response.community.PostCommentUserResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.CommunityPost;
import com.skillsprint.entity.ContentReport;
import com.skillsprint.entity.PostComment;
import com.skillsprint.entity.PostLike;
import com.skillsprint.entity.User;
import com.skillsprint.enums.community.CommunityPostStatus;
import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import com.skillsprint.enums.community.PostCommentStatus;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.CommunityPostRepository;
import com.skillsprint.repository.ContentReportRepository;
import com.skillsprint.repository.PostCommentRepository;
import com.skillsprint.repository.PostLikeRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.subscription.PlanFeatureKeys;
import com.skillsprint.service.subscription.QuotaService;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
public class CommunityService {

    static int MAX_PAGE_SIZE = 100;
    static int MAX_POST_LENGTH = 5000;
    static int MAX_COMMENT_LENGTH = 1000;
    static int POST_AUTO_HIDE_REPORT_THRESHOLD = 5;
    static int COMMENT_AUTO_HIDE_REPORT_THRESHOLD = 3;

    CommunityPostRepository communityPostRepository;
    PostLikeRepository postLikeRepository;
    PostCommentRepository postCommentRepository;
    ContentReportRepository contentReportRepository;
    UserRepository userRepository;
    CommunityBlacklistService blacklistService;
    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;
    QuotaService quotaService;

    @Transactional
    public CommunityUserPostResponse createPost(String userId, CreateCommunityPostRequest request) {
        validateCommunityFeed(userId);
        User user = findUser(userId);
        String content = normalizeRequiredContent(request.getContent(), MAX_POST_LENGTH);

        CommunityPost post = new CommunityPost();
        post.setAuthor(user);
        post.setContent(content);
        post.setHashtags(serializeHashtags(request.getHashtags()));
        post.setStatus(resolvePostStatus(content));

        return toUserPostResponse(communityPostRepository.save(post), userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityUserPostResponse> getFeed(
            String userId,
            String search,
            String hashtag,
            int page,
            int size
    ) {
        validateCommunityFeed(userId);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<CommunityUserPostResponse> posts = communityPostRepository
                .searchByStatus(
                        CommunityPostStatus.APPROVED,
                        normalizeSearch(search),
                        normalizeHashtagFilter(hashtag),
                        pageable
                )
                .map(post -> toUserPostResponse(post, userId));

        return PageResponse.from(posts);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityUserPostResponse> getMyPosts(
            String userId,
            CommunityPostStatus status,
            int page,
            int size
    ) {
        validateCommunityFeed(userId);
        findUser(userId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<CommunityUserPostResponse> posts = communityPostRepository
                .findMyPosts(userId, status, pageable)
                .map(post -> toUserPostResponse(post, userId));

        return PageResponse.from(posts);
    }

    @Transactional(readOnly = true)
    public CommunityUserPostResponse getPost(String userId, UUID postId) {
        validateCommunityFeed(userId);
        CommunityPost post = findPost(postId);
        if (post.getStatus() != CommunityPostStatus.APPROVED
                && !post.getAuthor().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        return toUserPostResponse(post, userId);
    }

    @Transactional
    public CommunityUserPostResponse updatePost(String userId, UUID postId, UpdateCommunityPostRequest request) {
        validateCommunityFeed(userId);
        CommunityPost post = findPost(postId);
        requireOwner(post.getAuthor(), userId);
        if (post.getStatus() == CommunityPostStatus.DELETED) {
            throw new AppException(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        String content = normalizeRequiredContent(request.getContent(), MAX_POST_LENGTH);
        post.setContent(content);
        post.setHashtags(serializeHashtags(request.getHashtags()));
        post.setStatus(resolvePostStatus(content));
        post.setAdminNote(null);

        return toUserPostResponse(communityPostRepository.save(post), userId);
    }

    @Transactional
    public void deletePost(String userId, UUID postId) {
        validateCommunityFeed(userId);
        CommunityPost post = findPost(postId);
        requireOwner(post.getAuthor(), userId);

        post.setStatus(CommunityPostStatus.DELETED);
        communityPostRepository.save(post);
    }

    @Transactional
    public CommunityUserPostResponse likePost(String userId, UUID postId) {
        validateCommunityFeed(userId);
        User user = findUser(userId);
        CommunityPost post = findVisiblePost(postId);

        if (!postLikeRepository.existsByPostPostIdAndUserUserId(postId, userId)) {
            PostLike like = new PostLike();
            like.setPost(post);
            like.setUser(user);
            postLikeRepository.save(like);

            post.setLikeCount(post.getLikeCount() + 1);
            communityPostRepository.save(post);
        }

        return toUserPostResponse(post, userId);
    }

    @Transactional
    public CommunityUserPostResponse unlikePost(String userId, UUID postId) {
        validateCommunityFeed(userId);
        CommunityPost post = findVisiblePost(postId);

        postLikeRepository.findByPostPostIdAndUserUserId(postId, userId)
                .ifPresent(like -> {
                    postLikeRepository.delete(like);
                    post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
                    communityPostRepository.save(post);
                });

        return toUserPostResponse(post, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostCommentUserResponse> getComments(String userId, UUID postId, int page, int size) {
        validateCommunityFeed(userId);
        findVisiblePost(postId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        Page<PostCommentUserResponse> comments = postCommentRepository
                .findByPostAndStatus(postId, PostCommentStatus.VISIBLE, pageable)
                .map(this::toUserCommentResponse);

        return PageResponse.from(comments);
    }

    @Transactional
    public PostCommentUserResponse createComment(String userId, UUID postId, CreatePostCommentRequest request) {
        validateCommunityFeed(userId);
        User user = findUser(userId);
        CommunityPost post = findVisiblePost(postId);
        String content = normalizeRequiredContent(request.getContent(), MAX_COMMENT_LENGTH);

        PostComment comment = new PostComment();
        comment.setPost(post);
        comment.setAuthor(user);
        comment.setContent(content);
        comment.setStatus(resolveCommentStatus(content));

        PostComment saved = postCommentRepository.save(comment);
        if (saved.getStatus() == PostCommentStatus.VISIBLE) {
            post.setCommentCount(post.getCommentCount() + 1);
            communityPostRepository.save(post);
        }

        return toUserCommentResponse(saved);
    }

    @Transactional
    public PostCommentUserResponse updateComment(String userId, UUID commentId, UpdatePostCommentRequest request) {
        validateCommunityFeed(userId);
        PostComment comment = findComment(commentId);
        requireOwner(comment.getAuthor(), userId);
        if (comment.getStatus() == PostCommentStatus.DELETED) {
            throw new AppException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }

        PostCommentStatus oldStatus = comment.getStatus();
        String content = normalizeRequiredContent(request.getContent(), MAX_COMMENT_LENGTH);
        comment.setContent(content);
        comment.setStatus(resolveCommentStatus(content));
        comment.setAdminNote(null);

        adjustCommentCountOnStatusChange(comment.getPost(), oldStatus, comment.getStatus());
        return toUserCommentResponse(postCommentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(String userId, UUID commentId) {
        validateCommunityFeed(userId);
        PostComment comment = findComment(commentId);
        requireOwner(comment.getAuthor(), userId);

        PostCommentStatus oldStatus = comment.getStatus();
        comment.setStatus(PostCommentStatus.DELETED);
        adjustCommentCountOnStatusChange(comment.getPost(), oldStatus, comment.getStatus());
        postCommentRepository.save(comment);
    }

    @Transactional
    public ContentReportResponse reportPost(String userId, UUID postId, CreateContentReportRequest request) {
        validateCommunityFeed(userId);
        findVisiblePost(postId);
        return createReport(userId, ContentReportTargetType.POST, postId, request);
    }

    @Transactional
    public ContentReportResponse reportComment(String userId, UUID commentId, CreateContentReportRequest request) {
        validateCommunityFeed(userId);
        PostComment comment = findComment(commentId);
        if (comment.getStatus() != PostCommentStatus.VISIBLE) {
            throw new AppException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }
        return createReport(userId, ContentReportTargetType.COMMENT, commentId, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommunityPostResponse> getAdminPosts(
            CommunityPostStatus status,
            String search,
            int page,
            int size
    ) {
        Pageable pageable = adminPageable(page, size);
        Page<CommunityPostResponse> posts = communityPostRepository
                .searchAdmin(status, normalizeSearch(search), pageable)
                .map(post -> toPostResponse(post, null));

        return PageResponse.from(posts);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostCommentResponse> getAdminComments(
            PostCommentStatus status,
            String search,
            int page,
            int size
    ) {
        Pageable pageable = adminPageable(page, size);
        Page<PostCommentResponse> comments = postCommentRepository
                .searchAdmin(status, normalizeSearch(search), pageable)
                .map(this::toCommentResponse);

        return PageResponse.from(comments);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContentReportResponse> getAdminReports(
            ContentReportTargetType targetType,
            ContentReportStatus status,
            int page,
            int size
    ) {
        Pageable pageable = adminPageable(page, size);
        Page<ContentReportResponse> reports = contentReportRepository
                .searchAdmin(targetType, status, pageable)
                .map(this::toReportResponse);

        return PageResponse.from(reports);
    }

    @Transactional
    public CommunityPostResponse updatePostStatus(
            String adminUserId,
            UUID postId,
            UpdateCommunityPostStatusRequest request
    ) {
        CommunityPost post = findPost(postId);
        Map<String, Object> oldValue = statusSnapshot(post.getStatus(), post.getAdminNote());
        post.setStatus(request.getStatus());
        post.setAdminNote(normalizeBlank(request.getAdminNote()));

        CommunityPost saved = communityPostRepository.save(post);
        logActivity(
                adminUserId,
                BusinessEntityType.COMMUNITY_POST,
                saved.getPostId(),
                BusinessActionType.COMMUNITY_POST_STATUS_UPDATED,
                "Cập nhật trạng thái bài viết cộng đồng",
                "Admin cập nhật trạng thái bài viết trong community",
                oldValue,
                statusSnapshot(saved.getStatus(), saved.getAdminNote())
        );
        return toPostResponse(saved, null);
    }

    @Transactional
    public PostCommentResponse updateCommentStatus(
            String adminUserId,
            UUID commentId,
            UpdatePostCommentStatusRequest request
    ) {
        PostComment comment = findComment(commentId);
        PostCommentStatus oldStatus = comment.getStatus();
        Map<String, Object> oldValue = statusSnapshot(oldStatus, comment.getAdminNote());
        comment.setStatus(request.getStatus());
        comment.setAdminNote(normalizeBlank(request.getAdminNote()));
        adjustCommentCountOnStatusChange(comment.getPost(), oldStatus, comment.getStatus());

        PostComment saved = postCommentRepository.save(comment);
        logActivity(
                adminUserId,
                BusinessEntityType.COMMUNITY_COMMENT,
                saved.getCommentId(),
                BusinessActionType.COMMUNITY_COMMENT_STATUS_UPDATED,
                "Cập nhật trạng thái bình luận cộng đồng",
                "Admin cập nhật trạng thái bình luận trong community",
                oldValue,
                statusSnapshot(saved.getStatus(), saved.getAdminNote())
        );
        return toCommentResponse(saved);
    }

    @Transactional
    public ContentReportResponse updateReportStatus(
            String adminUserId,
            UUID reportId,
            UpdateContentReportStatusRequest request
    ) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_REPORT_NOT_FOUND));

        Map<String, Object> oldValue = statusSnapshot(report.getStatus(), report.getAdminNote());
        report.setStatus(request.getStatus());
        report.setAdminNote(normalizeBlank(request.getAdminNote()));
        report.setReviewedBy(findUser(adminUserId));
        report.setReviewedAt(Instant.now());

        ContentReport saved = contentReportRepository.save(report);
        logActivity(
                adminUserId,
                BusinessEntityType.CONTENT_REPORT,
                saved.getReportId(),
                BusinessActionType.COMMUNITY_REPORT_STATUS_UPDATED,
                "Cập nhật trạng thái report cộng đồng",
                "Admin xử lý report nội dung community",
                oldValue,
                statusSnapshot(saved.getStatus(), saved.getAdminNote())
        );
        return toReportResponse(saved);
    }

    private ContentReportResponse createReport(
            String userId,
            ContentReportTargetType targetType,
            UUID targetId,
            CreateContentReportRequest request
    ) {
        User user = findUser(userId);
        contentReportRepository
                .findByTargetTypeAndTargetIdAndReporterUserId(targetType, targetId, userId)
                .ifPresent(report -> {
                    throw new AppException(ErrorCode.COMMUNITY_REPORT_DUPLICATED);
                });

        ContentReport report = new ContentReport();
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporter(user);
        report.setReason(normalizeReportReason(request.getReason()));

        ContentReport saved = contentReportRepository.save(report);
        applyReportThreshold(targetType, targetId);
        return toReportResponse(saved);
    }

    private void applyReportThreshold(ContentReportTargetType targetType, UUID targetId) {
        long pendingReports = contentReportRepository.countByTargetTypeAndTargetIdAndStatus(
                targetType,
                targetId,
                ContentReportStatus.PENDING
        );

        if (targetType == ContentReportTargetType.POST) {
            CommunityPost post = findPost(targetId);
            post.setReportCount((int) pendingReports);
            if (pendingReports >= POST_AUTO_HIDE_REPORT_THRESHOLD
                    && post.getStatus() == CommunityPostStatus.APPROVED) {
                post.setStatus(CommunityPostStatus.HIDDEN);
                post.setAdminNote("Tự ẩn do vượt ngưỡng report");
            }
            communityPostRepository.save(post);
            return;
        }

        PostComment comment = findComment(targetId);
        PostCommentStatus oldStatus = comment.getStatus();
        comment.setReportCount((int) pendingReports);
        if (pendingReports >= COMMENT_AUTO_HIDE_REPORT_THRESHOLD
                && comment.getStatus() == PostCommentStatus.VISIBLE) {
            comment.setStatus(PostCommentStatus.HIDDEN);
            comment.setAdminNote("Tự ẩn do vượt ngưỡng report");
        }
        adjustCommentCountOnStatusChange(comment.getPost(), oldStatus, comment.getStatus());
        postCommentRepository.save(comment);
    }

    private CommunityPost findVisiblePost(UUID postId) {
        CommunityPost post = findPost(postId);
        if (post.getStatus() != CommunityPostStatus.APPROVED) {
            throw new AppException(ErrorCode.COMMUNITY_POST_NOT_VISIBLE);
        }
        return post;
    }

    private CommunityPost findPost(UUID postId) {
        return communityPostRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private PostComment findComment(UUID commentId) {
        return postCommentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateCommunityFeed(String userId) {
        quotaService.validateFeature(userId, PlanFeatureKeys.COMMUNITY_FEED);
    }

    private void requireOwner(User owner, String userId) {
        if (owner == null || !owner.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.COMMUNITY_ACTION_NOT_ALLOWED);
        }
    }

    private CommunityPostStatus resolvePostStatus(String content) {
        return blacklistService.containsBadWords(content)
                ? CommunityPostStatus.PENDING_MODERATION
                : CommunityPostStatus.APPROVED;
    }

    private PostCommentStatus resolveCommentStatus(String content) {
        return blacklistService.containsBadWords(content)
                ? PostCommentStatus.PENDING_MODERATION
                : PostCommentStatus.VISIBLE;
    }

    private void adjustCommentCountOnStatusChange(
            CommunityPost post,
            PostCommentStatus oldStatus,
            PostCommentStatus newStatus
    ) {
        if (oldStatus == newStatus) {
            return;
        }

        boolean wasVisible = oldStatus == PostCommentStatus.VISIBLE;
        boolean isVisible = newStatus == PostCommentStatus.VISIBLE;
        if (wasVisible == isVisible) {
            return;
        }

        int nextCount = post.getCommentCount() + (isVisible ? 1 : -1);
        post.setCommentCount(Math.max(0, nextCount));
        communityPostRepository.save(post);
    }

    private Pageable adminPageable(int page, int size) {
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

    private String normalizeRequiredContent(String content, int maxLength) {
        String normalized = normalizeBlank(content);
        if (normalized == null) {
            throw new AppException(ErrorCode.COMMUNITY_CONTENT_REQUIRED);
        }
        if (normalized.length() > maxLength) {
            throw new AppException(ErrorCode.COMMUNITY_CONTENT_TOO_LONG);
        }
        return normalized;
    }

    private String normalizeReportReason(String reason) {
        String normalized = normalizeBlank(reason);
        if (normalized == null) {
            return "Người dùng báo cáo nội dung không phù hợp";
        }
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new AppException(ErrorCode.COMMUNITY_CONTENT_TOO_LONG);
        }
        return normalized;
    }

    private String normalizeSearch(String search) {
        return normalizeBlank(search);
    }

    private String normalizeHashtagFilter(String hashtag) {
        return normalizeHashtag(hashtag);
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String serializeHashtags(List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return null;
        }

        Set<String> normalized = new LinkedHashSet<>();
        hashtags.stream()
                .map(this::normalizeHashtag)
                .filter(tag -> tag != null && !tag.isBlank())
                .limit(10)
                .forEach(normalized::add);

        return normalized.isEmpty() ? null : String.join("\n", normalized);
    }

    private List<String> deserializeHashtags(String hashtags) {
        if (hashtags == null || hashtags.isBlank()) {
            return List.of();
        }

        return Arrays.stream(hashtags.split("\\n"))
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private String normalizeHashtag(String hashtag) {
        String normalized = normalizeBlank(hashtag);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.startsWith("#") ? normalized.substring(1) : normalized;
        normalized = normalized.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 50) {
            normalized = normalized.substring(0, 50);
        }
        return "#" + normalized;
    }

    private CommunityPostResponse toPostResponse(CommunityPost post, String currentUserId) {
        boolean likedByMe = currentUserId != null
                && postLikeRepository.existsByPostPostIdAndUserUserId(post.getPostId(), currentUserId);

        return CommunityPostResponse.builder()
                .postId(post.getPostId())
                .author(toAuthor(post.getAuthor()))
                .content(post.getContent())
                .hashtags(deserializeHashtags(post.getHashtags()))
                .status(post.getStatus())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .reportCount(post.getReportCount())
                .likedByMe(likedByMe)
                .adminNote(post.getAdminNote())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private CommunityUserPostResponse toUserPostResponse(CommunityPost post, String currentUserId) {
        boolean likedByMe = currentUserId != null
                && postLikeRepository.existsByPostPostIdAndUserUserId(post.getPostId(), currentUserId);

        return CommunityUserPostResponse.builder()
                .postId(post.getPostId())
                .author(toAuthor(post.getAuthor()))
                .content(post.getContent())
                .hashtags(deserializeHashtags(post.getHashtags()))
                .status(post.getStatus())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .likedByMe(likedByMe)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private PostCommentResponse toCommentResponse(PostComment comment) {
        return PostCommentResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPost().getPostId())
                .author(toAuthor(comment.getAuthor()))
                .content(comment.getContent())
                .status(comment.getStatus())
                .reportCount(comment.getReportCount())
                .adminNote(comment.getAdminNote())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private PostCommentUserResponse toUserCommentResponse(PostComment comment) {
        return PostCommentUserResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPost().getPostId())
                .author(toAuthor(comment.getAuthor()))
                .content(comment.getContent())
                .status(comment.getStatus())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
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

    private void logActivity(
            String adminUserId,
            BusinessEntityType entityType,
            UUID entityId,
            BusinessActionType actionType,
            String title,
            String description,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (adminUserId != null && !adminUserId.isBlank()) {
            userRepository.findById(adminUserId).ifPresent(log::setUser);
        }
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActionType(actionType);
        log.setTitle(title);
        log.setDescription(description);
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("adminUserId", adminUserId);
        metadata.put("module", "COMMUNITY");
        log.setMetadata(toJson(metadata));

        activityLogRepository.save(log);
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

    private Map<String, Object> statusSnapshot(Enum<?> status, String adminNote) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", status);
        snapshot.put("adminNote", adminNote);
        return snapshot;
    }
}
