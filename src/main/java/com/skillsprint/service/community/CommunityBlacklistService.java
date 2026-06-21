package com.skillsprint.service.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.community.CreateBlacklistKeywordRequest;
import com.skillsprint.dto.response.community.BlacklistKeywordResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.BlacklistKeyword;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BlacklistKeywordRepository;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommunityBlacklistService {

    final BlacklistKeywordRepository blacklistKeywordRepository;
    final UserRepository userRepository;
    final BusinessActivityLogRepository activityLogRepository;
    final ObjectMapper objectMapper;

    volatile Set<String> cachedKeywords = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void loadCache() {
        refreshCache();
    }

    @Transactional(readOnly = true)
    public List<BlacklistKeywordResponse> getKeywords() {
        return blacklistKeywordRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(BlacklistKeyword::getKeyword))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BlacklistKeywordResponse addKeyword(String adminUserId, CreateBlacklistKeywordRequest request) {
        String keyword = normalizeKeyword(request.getKeyword());
        if (keyword == null) {
            throw new AppException(ErrorCode.COMMUNITY_CONTENT_REQUIRED);
        }
        if (blacklistKeywordRepository.existsByKeyword(keyword)) {
            throw new AppException(ErrorCode.BLACKLIST_KEYWORD_DUPLICATED);
        }

        BlacklistKeyword item = new BlacklistKeyword();
        item.setKeyword(keyword);
        item.setCreatedBy(findUser(adminUserId));

        BlacklistKeyword saved = blacklistKeywordRepository.save(item);
        refreshCache();
        logKeywordActivity(
                adminUserId,
                saved,
                BusinessActionType.BLACKLIST_KEYWORD_CREATED,
                "Thêm từ khóa blacklist",
                null,
                keywordSnapshot(saved)
        );
        return toResponse(saved);
    }

    @Transactional
    public void deleteKeyword(String adminUserId, Long wordId) {
        BlacklistKeyword item = blacklistKeywordRepository.findById(wordId)
                .orElseThrow(() -> new AppException(ErrorCode.BLACKLIST_KEYWORD_NOT_FOUND));
        Map<String, Object> oldValue = keywordSnapshot(item);

        blacklistKeywordRepository.delete(item);
        refreshCache();
        logKeywordActivity(
                adminUserId,
                item,
                BusinessActionType.BLACKLIST_KEYWORD_DELETED,
                "Xóa từ khóa blacklist",
                oldValue,
                null
        );
    }

    public boolean containsBadWords(String content) {
        String normalized = normalizeForCheck(content);
        if (normalized == null) {
            return false;
        }

        return cachedKeywords.stream().anyMatch(normalized::contains);
    }

    public void refreshCache() {
        Set<String> nextCache = ConcurrentHashMap.newKeySet();
        blacklistKeywordRepository.findAll()
                .stream()
                .map(BlacklistKeyword::getKeyword)
                .map(this::normalizeForCheck)
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .forEach(nextCache::add);

        cachedKeywords = nextCache;
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private String normalizeKeyword(String keyword) {
        String normalized = normalizeForCheck(keyword);
        if (normalized == null || normalized.length() > 100) {
            return normalized;
        }
        return normalized;
    }

    private String normalizeForCheck(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String withoutAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT);
    }

    private BlacklistKeywordResponse toResponse(BlacklistKeyword item) {
        return BlacklistKeywordResponse.builder()
                .wordId(item.getWordId())
                .keyword(item.getKeyword())
                .createdBy(toAuthor(item.getCreatedBy()))
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
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

    private void logKeywordActivity(
            String adminUserId,
            BlacklistKeyword item,
            BusinessActionType actionType,
            String title,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (adminUserId != null && !adminUserId.isBlank()) {
            userRepository.findById(adminUserId).ifPresent(log::setUser);
        }
        log.setEntityType(BusinessEntityType.BLACKLIST_KEYWORD);
        log.setActionType(actionType);
        log.setTitle(title);
        log.setDescription("Admin cập nhật blacklist community");
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("wordId", item.getWordId());
        metadata.put("keyword", item.getKeyword());
        metadata.put("adminUserId", adminUserId);
        metadata.put("module", "COMMUNITY");
        log.setMetadata(toJson(metadata));

        activityLogRepository.save(log);
    }

    private Map<String, Object> keywordSnapshot(BlacklistKeyword item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("wordId", item.getWordId());
        snapshot.put("keyword", item.getKeyword());
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
