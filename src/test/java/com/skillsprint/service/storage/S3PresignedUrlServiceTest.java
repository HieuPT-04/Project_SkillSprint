package com.skillsprint.service.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3PresignedUrlServiceTest {

    @Mock
    S3Presigner s3Presigner;

    @Mock
    S3Client s3Client;

    S3PresignedUrlService s3PresignedUrlService;

    @BeforeEach
    void setUp() {
        s3PresignedUrlService = new S3PresignedUrlService(
                s3Presigner,
                s3Client,
                new S3Properties(
                        "ap-southeast-1",
                        "test-bucket",
                        "https://cdn.example.com/",
                        null,
                        null,
                        10
                )
        );
    }

    @Test
    void createAvatarUploadUrlBuildsOwnedObjectKeyAndPresignedUrls() {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPut("https://s3.example.com/avatar-put"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGet("https://s3.example.com/avatar-get"));

        AvatarUploadUrlResponse response = s3PresignedUrlService.createAvatarUploadUrl(
                "user-1",
                avatarRequest("  profile.jpeg  ", " Image/JPEG ")
        );

        assertEquals("https://s3.example.com/avatar-put", response.getUploadUrl());
        assertEquals("https://s3.example.com/avatar-get", response.getFileUrl());
        assertTrue(response.getObjectKey().startsWith("users/user-1/avatar/"));
        assertTrue(response.getObjectKey().endsWith(".jpg"));
        assertNotNull(response.getExpiresAt());

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertEquals(10, captor.getValue().signatureDuration().toMinutes());
        assertEquals("test-bucket", captor.getValue().putObjectRequest().bucket());
        assertEquals(response.getObjectKey(), captor.getValue().putObjectRequest().key());
        assertEquals("image/jpeg", captor.getValue().putObjectRequest().contentType());
    }

    @Test
    void createFeedbackImageUploadUrlAllowsGifAndUsesFeedbackPrefix() {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPut("https://s3.example.com/feedback-put"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGet("https://s3.example.com/feedback-get"));

        FeedbackUploadUrlResponse response = s3PresignedUrlService.createFeedbackImageUploadUrl(
                "user-1",
                feedbackRequest("evidence.gif", "image/gif")
        );

        assertEquals("https://s3.example.com/feedback-put", response.getUploadUrl());
        assertEquals("https://s3.example.com/feedback-get", response.getFileUrl());
        assertTrue(response.getObjectKey().startsWith("feedback/user-1/"));
        assertTrue(response.getObjectKey().endsWith(".gif"));
    }

    @Test
    void createCreatorPayoutQrUploadUrlUsesPrivateCreatorPrefixWithoutAViewUrl() {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPut("https://s3.example.com/payout-qr-put"));

        CreatorPayoutQrUploadUrlResponse response = s3PresignedUrlService.createCreatorPayoutQrUploadUrl(
                "creator-1",
                payoutQrRequest("bank-qr.webp", "image/webp")
        );

        assertEquals("https://s3.example.com/payout-qr-put", response.getUploadUrl());
        assertTrue(response.getObjectKey().startsWith("creator-payouts/creator-1/qr/"));
        assertTrue(response.getObjectKey().endsWith(".webp"));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void confirmCreatorPayoutQrRejectsForeignKeyBeforeCallingS3() {
        AppException exception = assertThrows(
                AppException.class,
                () -> s3PresignedUrlService.confirmCreatorPayoutQrUpload(
                        "creator-1", "creator-payouts/creator-2/qr/other.png")
        );

        assertEquals(ErrorCode.INVALID_CREATOR_PAYOUT_QR_OBJECT_KEY, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void confirmCreatorPayoutQrRequiresAllowedImageMetadataAndSize() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png")
                .contentLength(5L * 1024 * 1024)
                .build());

        String key = s3PresignedUrlService.confirmCreatorPayoutQrUpload(
                "creator-1", "creator-payouts/creator-1/qr/bank.png");

        assertEquals("creator-payouts/creator-1/qr/bank.png", key);
    }

    @Test
    void confirmAvatarUploadChecksOwnedKeyBeforeHeadObject() {
        ConfirmAvatarUploadRequest request = confirmAvatar("  users/user-1/avatar/picture.png  ");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        String objectKey = s3PresignedUrlService.confirmAvatarUpload("user-1", request);

        assertEquals("users/user-1/avatar/picture.png", objectKey);
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("users/user-1/avatar/picture.png", captor.getValue().key());
    }

    @Test
    void confirmAvatarUploadRejectsForeignObjectKeyWithoutCallingS3() {
        AppException exception = assertThrows(
                AppException.class,
                () -> s3PresignedUrlService.confirmAvatarUpload(
                        "user-1",
                        confirmAvatar("users/user-2/avatar/picture.png")
                )
        );

        assertEquals(ErrorCode.INVALID_AVATAR_OBJECT_KEY, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void confirmFeedbackImageMapsMissingS3ObjectToDomainError() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder()
                .statusCode(404)
                .message("not found")
                .build());

        AppException exception = assertThrows(
                AppException.class,
                () -> s3PresignedUrlService.confirmFeedbackImage("user-1", "feedback/user-1/image.png")
        );

        assertEquals(ErrorCode.FEEDBACK_IMAGE_NOT_UPLOADED, exception.getErrorCode());
    }

    @Test
    void createViewUrlReturnsNullForBlankObjectKey() {
        assertNull(s3PresignedUrlService.createViewUrl("  "));

        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }

    private CreateAvatarUploadUrlRequest avatarRequest(String fileName, String contentType) {
        CreateAvatarUploadUrlRequest request = new CreateAvatarUploadUrlRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        return request;
    }

    private CreateFeedbackUploadUrlRequest feedbackRequest(String fileName, String contentType) {
        CreateFeedbackUploadUrlRequest request = new CreateFeedbackUploadUrlRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        return request;
    }

    private CreateCreatorPayoutQrUploadUrlRequest payoutQrRequest(String fileName, String contentType) {
        CreateCreatorPayoutQrUploadUrlRequest request = new CreateCreatorPayoutQrUploadUrlRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        return request;
    }

    private ConfirmAvatarUploadRequest confirmAvatar(String objectKey) {
        ConfirmAvatarUploadRequest request = new ConfirmAvatarUploadRequest();
        request.setObjectKey(objectKey);
        return request;
    }

    private PresignedPutObjectRequest presignedPut(String url) {
        return PresignedPutObjectRequest.builder()
                .expiration(Instant.now().plusSeconds(600))
                .isBrowserExecutable(false)
                .signedHeaders(Map.of("host", List.of("s3.example.com")))
                .httpRequest(SdkHttpFullRequest.builder()
                        .uri(URI.create(url))
                        .method(SdkHttpMethod.PUT)
                        .build())
                .build();
    }

    private PresignedGetObjectRequest presignedGet(String url) {
        return PresignedGetObjectRequest.builder()
                .expiration(Instant.now().plusSeconds(600))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", List.of("s3.example.com")))
                .httpRequest(SdkHttpFullRequest.builder()
                        .uri(URI.create(url))
                        .method(SdkHttpMethod.GET)
                        .build())
                .build();
    }
}
