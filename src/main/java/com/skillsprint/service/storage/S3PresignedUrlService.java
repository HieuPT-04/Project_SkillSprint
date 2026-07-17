package com.skillsprint.service.storage;

import com.skillsprint.configuration.s3.S3Properties;
import com.skillsprint.dto.request.feedback.CreateFeedbackUploadUrlRequest;
import com.skillsprint.dto.request.marketplace.CreateCreatorPayoutQrUploadUrlRequest;
import com.skillsprint.dto.request.user.ConfirmAvatarUploadRequest;
import com.skillsprint.dto.request.user.CreateAvatarUploadUrlRequest;
import com.skillsprint.dto.response.feedback.FeedbackUploadUrlResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutQrUploadUrlResponse;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
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

    private static final Set<String> ALLOWED_FEEDBACK_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> ALLOWED_CREATOR_PAYOUT_QR_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final long MAX_CREATOR_PAYOUT_QR_BYTES = 5L * 1024 * 1024;

    S3Presigner s3Presigner;
    S3Client s3Client;
    S3Properties properties;

    public AvatarUploadUrlResponse createAvatarUploadUrl(String userId, CreateAvatarUploadUrlRequest request) {
        String contentType = request.getContentType().trim().toLowerCase();
        if (!ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.INVALID_AVATAR_CONTENT_TYPE);
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
                .fileUrl(createViewUrl(objectKey))
                .objectKey(objectKey)
                .expiresAt(expiresAt)
                .build();
    }

    public FeedbackUploadUrlResponse createFeedbackImageUploadUrl(String userId, CreateFeedbackUploadUrlRequest request) {
        String contentType = request.getContentType().trim().toLowerCase();
        if (!ALLOWED_FEEDBACK_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.INVALID_FEEDBACK_IMAGE_CONTENT_TYPE);
        }

        String objectKey = buildFeedbackImageObjectKey(userId, contentType);
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

        return FeedbackUploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .fileUrl(createViewUrl(objectKey))
                .objectKey(objectKey)
                .expiresAt(expiresAt)
                .build();
    }

    /** Creates a private, Creator-scoped QR upload URL. The caller later submits only the object key. */
    public CreatorPayoutQrUploadUrlResponse createCreatorPayoutQrUploadUrl(
            String userId,
            CreateCreatorPayoutQrUploadUrlRequest request
    ) {
        String contentType = request.getContentType().trim().toLowerCase();
        if (!ALLOWED_CREATOR_PAYOUT_QR_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(ErrorCode.INVALID_CREATOR_PAYOUT_QR_CONTENT_TYPE);
        }

        String objectKey = buildCreatorPayoutQrObjectKey(userId, request.getFileName(), contentType);
        Duration signatureDuration = Duration.ofMinutes(properties.uploadUrlExpirationMinutes());
        Instant expiresAt = Instant.now().plus(signatureDuration);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        String uploadUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(putObjectRequest)
                .build()).url().toString();

        return CreatorPayoutQrUploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .objectKey(objectKey)
                .expiresAt(expiresAt)
                .build();
    }

    /** Validates the private Creator QR prefix and uploaded object metadata before persistence. */
    public String confirmCreatorPayoutQrUpload(String userId, String objectKey) {
        String key = objectKey.trim();
        if (!key.startsWith("creator-payouts/%s/".formatted(userId))) {
            throw new AppException(ErrorCode.INVALID_CREATOR_PAYOUT_QR_OBJECT_KEY);
        }

        try {
            HeadObjectResponse object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            String contentType = object.contentType() == null ? "" : object.contentType().trim().toLowerCase();
            if (!ALLOWED_CREATOR_PAYOUT_QR_CONTENT_TYPES.contains(contentType)
                    || object.contentLength() == null
                    || object.contentLength() > MAX_CREATOR_PAYOUT_QR_BYTES) {
                throw new AppException(ErrorCode.CREATOR_PAYOUT_QR_NOT_UPLOADED);
            }
        } catch (S3Exception ex) {
            throw new AppException(ErrorCode.CREATOR_PAYOUT_QR_NOT_UPLOADED);
        }

        return key;
    }

    /**
     * Validates that {@code objectKey} belongs to {@code userId} and that the file was actually
     * uploaded to S3, then returns the normalized key. Mirrors {@link #confirmAvatarUpload}.
     */
    public String confirmFeedbackImage(String userId, String objectKey) {
        String key = objectKey.trim();
        String expectedPrefix = "feedback/%s/".formatted(userId);
        if (!key.startsWith(expectedPrefix)) {
            throw new AppException(ErrorCode.INVALID_FEEDBACK_IMAGE_OBJECT_KEY);
        }

        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .build()
            );
        } catch (S3Exception ex) {
            throw new AppException(ErrorCode.FEEDBACK_IMAGE_NOT_UPLOADED);
        }

        return key;
    }

    public String createViewUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.uploadUrlExpirationMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public String confirmAvatarUpload(String userId, ConfirmAvatarUploadRequest request) {
        String objectKey = request.getObjectKey().trim();
        String expectedPrefix = "users/%s/avatar/".formatted(userId);
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new AppException(ErrorCode.INVALID_AVATAR_OBJECT_KEY);
        }

        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build()
            );
        } catch (S3Exception ex) {
            throw new AppException(ErrorCode.AVATAR_NOT_UPLOADED);
        }

        return objectKey;
    }

    private String buildFeedbackImageObjectKey(String userId, String contentType) {
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> throw new AppException(ErrorCode.INVALID_FEEDBACK_IMAGE_CONTENT_TYPE);
        };
        return "feedback/%s/%s.%s".formatted(userId, UUID.randomUUID(), extension);
    }

    private String buildCreatorPayoutQrObjectKey(String userId, String fileName, String contentType) {
        return "creator-payouts/%s/qr/%s.%s".formatted(userId, UUID.randomUUID(), resolveExtension(fileName, contentType));
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
            default -> throw new AppException(ErrorCode.INVALID_AVATAR_CONTENT_TYPE);
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

        throw new AppException(ErrorCode.INVALID_AVATAR_FILE_EXTENSION);
    }

}
