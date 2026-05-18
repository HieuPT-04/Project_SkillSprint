package com.skillsprint.service.storage;

import com.skillsprint.configuration.s3.S3Properties;
import com.skillsprint.dto.request.user.ConfirmAvatarUploadRequest;
import com.skillsprint.dto.request.user.CreateAvatarUploadUrlRequest;
import com.skillsprint.dto.response.user.AvatarUploadUrlResponse;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class S3PresignedUrlService {

    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    S3Presigner s3Presigner;
    S3Client s3Client;
    S3Properties properties;

    public AvatarUploadUrlResponse createAvatarUploadUrl(String userId, CreateAvatarUploadUrlRequest request) {
        String contentType = request.getContentType().trim().toLowerCase();
        if (!ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Ảnh đại diện chỉ hỗ trợ JPG, PNG hoặc WEBP");
        }

        String objectKey = buildAvatarObjectKey(userId, request.getFileName(), contentType);
        Duration signatureDuration = Duration.ofMinutes(properties.uploadUrlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(signatureDuration);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return AvatarUploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .fileUrl(buildFileUrl(objectKey))
                .objectKey(objectKey)
                .expiresAt(expiresAt)
                .build();
    }

    public String confirmAvatarUpload(String userId, ConfirmAvatarUploadRequest request) {
        String objectKey = request.getObjectKey().trim();
        String expectedPrefix = "users/%s/avatar/".formatted(userId);
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xác nhận ảnh này");
        }

        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build()
            );
        } catch (S3Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Ảnh chưa được upload thành công lên S3");
        }

        return buildFileUrl(objectKey);
    }

    private String buildAvatarObjectKey(String userId, String fileName, String contentType) {
        String extension = resolveExtension(fileName, contentType);
        return "users/%s/avatar/%s.%s".formatted(userId, UUID.randomUUID(), extension);
    }

    private String resolveExtension(String fileName, String contentType) {
        String fileExtension = getFileExtension(fileName);
        if (!fileExtension.isBlank()) {
            return fileExtension;
        }

        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new AppException(ErrorCode.VALIDATION_ERROR, "Định dạng ảnh không hợp lệ");
        };
    }

    private String getFileExtension(String fileName) {
        String normalizedFileName = fileName.trim().toLowerCase();
        int dotIndex = normalizedFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == normalizedFileName.length() - 1) {
            return "";
        }

        String extension = normalizedFileName.substring(dotIndex + 1);
        if (extension.equals("jpeg")) {
            return "jpg";
        }
        if (extension.equals("jpg") || extension.equals("png") || extension.equals("webp")) {
            return extension;
        }

        throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên file ảnh phải có đuôi JPG, PNG hoặc WEBP");
    }

    private String buildFileUrl(String objectKey) {
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/" + objectKey;
    }
}
