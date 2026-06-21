package com.skillsprint.configuration;

import com.skillsprint.entity.BlacklistKeyword;
import com.skillsprint.repository.BlacklistKeywordRepository;
import com.skillsprint.service.community.CommunityBlacklistService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommunityBlacklistSeeder implements ApplicationRunner {

    BlacklistKeywordRepository blacklistKeywordRepository;
    CommunityBlacklistService blacklistService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> defaultKeywords = List.of(
                "spam",
                "scam",
                "lua dao",
                "co bac",
                "ma tuy",
                "18+",
                "sex"
        );

        defaultKeywords.forEach(this::ensureKeyword);
        blacklistService.refreshCache();
    }

    private void ensureKeyword(String keyword) {
        if (blacklistKeywordRepository.existsByKeyword(keyword)) {
            return;
        }

        BlacklistKeyword item = new BlacklistKeyword();
        item.setKeyword(keyword);
        blacklistKeywordRepository.save(item);
        log.info("Seeded community blacklist keyword {}", keyword);
    }
}
