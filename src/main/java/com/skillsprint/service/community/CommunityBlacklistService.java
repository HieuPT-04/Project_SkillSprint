package com.skillsprint.service.community;

import com.skillsprint.dto.request.community.CreateBlacklistKeywordRequest;
import com.skillsprint.dto.response.community.BlacklistKeywordResponse;
import com.skillsprint.dto.response.community.CommunityAuthorResponse;
import com.skillsprint.entity.BlacklistKeyword;
import com.skillsprint.entity.User;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BlacklistKeywordRepository;
import com.skillsprint.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        return toResponse(saved);
    }

    @Transactional
    public void deleteKeyword(Long wordId) {
        BlacklistKeyword item = blacklistKeywordRepository.findById(wordId)
                .orElseThrow(() -> new AppException(ErrorCode.BLACKLIST_KEYWORD_NOT_FOUND));

        blacklistKeywordRepository.delete(item);
        refreshCache();
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
}
