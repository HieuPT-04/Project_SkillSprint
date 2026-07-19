package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.MarketplacePackVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceQualityFingerprint {

    ObjectMapper objectMapper;

    public String of(MarketplacePackVersion version) {
        try {
            String canonical = version.getChapterCount() + "\n"
                    + version.getQuizCount() + "\n"
                    + version.getQuestionCount() + "\n"
                    + objectMapper.writeValueAsString(version.getContent());
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint Marketplace snapshot", exception);
        }
    }
}
