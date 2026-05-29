package com.skillsprint.service.material;

import com.skillsprint.dto.response.material.MaterialProcessingJobResponse;
import com.skillsprint.entity.ExtractedDocument;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.MaterialProcessingJob;
import com.skillsprint.entity.UploadedMaterial;
import com.skillsprint.enums.material.ExtractionStatus;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import com.skillsprint.enums.material.ProcessingJobStatus;
import com.skillsprint.enums.material.ProcessingStep;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.MaterialMapper;
import com.skillsprint.repository.ExtractedDocumentRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.MaterialProcessingJobRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import com.skillsprint.service.notification.NotificationService;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaterialProcessingService {

    static int MAX_CHUNK_LENGTH = 1_800;
    static int MIN_CHUNK_LENGTH = 400;

    UploadedMaterialRepository uploadedMaterialRepository;
    MaterialProcessingJobRepository materialProcessingJobRepository;
    ExtractedDocumentRepository extractedDocumentRepository;
    MaterialChunkRepository materialChunkRepository;
    MaterialTextExtractor materialTextExtractor;
    MaterialMapper materialMapper;
    S3Client s3Client;
    NotificationService notificationService;

    @Scheduled(fixedDelayString = "${app.material.processing.fixed-delay-ms:10000}")
    @Transactional
    public void processNextPendingJob() {
        materialProcessingJobRepository
                .findFirstByStatusOrderByCreatedAtAsc(ProcessingJobStatus.PENDING)
                .ifPresent(this::processJob);
    }

    @Transactional(readOnly = true)
    public MaterialProcessingJobResponse getLatestJob(String userId, UUID workspaceId, UUID materialId) {
        UploadedMaterial material = uploadedMaterialRepository
                .findByMaterialIdAndWorkspaceWorkspaceIdAndUserUserId(materialId, workspaceId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_NOT_FOUND));

        MaterialProcessingJob job = materialProcessingJobRepository
                .findTopByMaterialMaterialIdOrderByCreatedAtDesc(material.getMaterialId())
                .orElseThrow(() -> new AppException(ErrorCode.MATERIAL_PROCESSING_JOB_NOT_FOUND));

        return materialMapper.toMaterialProcessingJobResponse(job);
    }

    private void processJob(MaterialProcessingJob job) {
        UploadedMaterial material = job.getMaterial();
        log.info("[MATERIAL] Processing job {} for material {}", job.getJobId(), material.getMaterialId());

        markRunning(job, material);

        try {
            byte[] fileBytes = downloadMaterial(material);

            job.setCurrentStep(ProcessingStep.EXTRACTING);
            job.setProgressPercent(25);
            material.setProcessingStatus(MaterialProcessingStatus.EXTRACTING);

            MaterialTextExtractor.ExtractedText extractedText =
                    materialTextExtractor.extract(fileBytes, material.getFileType());
            String cleanedText = cleanText(extractedText.getText());

            ExtractedDocument document = saveExtractedDocument(material, extractedText, cleanedText);

            if (cleanedText.isBlank()) {
                failJob(job, material, document, ErrorCode.MATERIAL_TEXT_EMPTY, ErrorCode.MATERIAL_TEXT_EMPTY.getMessage());
                return;
            }

            job.setCurrentStep(ProcessingStep.CHUNKING);
            job.setProgressPercent(65);
            material.setProcessingStatus(MaterialProcessingStatus.CHUNKING);

            materialChunkRepository.deleteByMaterialMaterialId(material.getMaterialId());
            List<String> chunks = splitIntoChunks(cleanedText);
            saveChunks(material, document, chunks);

            job.setCurrentStep(ProcessingStep.SAVING_RESULT);
            job.setProgressPercent(100);
            job.setStatus(ProcessingJobStatus.COMPLETED);
            job.setFinishedAt(Instant.now());
            material.setProcessingStatus(MaterialProcessingStatus.COMPLETED);
            material.setErrorMessage(null);

            log.info("[MATERIAL] Completed job {} with {} chunks", job.getJobId(), chunks.size());
            notificationService.notifyMaterialProcessingCompleted(material);
        } catch (AppException ex) {
            failJob(job, material, null, ex.getErrorCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("[MATERIAL] Unexpected processing error for job {}", job.getJobId(), ex);
            failJob(job, material, null, ErrorCode.MATERIAL_PROCESSING_FAILED, ex.getMessage());
        }
    }

    private void markRunning(MaterialProcessingJob job, UploadedMaterial material) {
        job.setStatus(ProcessingJobStatus.RUNNING);
        job.setCurrentStep(ProcessingStep.EXTRACTING);
        job.setProgressPercent(10);
        job.setStartedAt(Instant.now());
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setRetryable(false);
        material.setProcessingStatus(MaterialProcessingStatus.EXTRACTING);
        material.setErrorMessage(null);
    }

    private byte[] downloadMaterial(UploadedMaterial material) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(material.getS3Bucket())
                .key(material.getS3ObjectKey())
                .build();

        return s3Client.getObjectAsBytes(request).asByteArray();
    }

    private ExtractedDocument saveExtractedDocument(
            UploadedMaterial material,
            MaterialTextExtractor.ExtractedText extractedText,
            String cleanedText
    ) {
        ExtractedDocument document = extractedDocumentRepository
                .findByMaterialMaterialId(material.getMaterialId())
                .orElseGet(ExtractedDocument::new);

        document.setMaterial(material);
        document.setWorkspace(material.getWorkspace());
        document.setExtractedText(extractedText.getText());
        document.setCleanedText(cleanedText);
        document.setTextLength(cleanedText.length());
        document.setPageCount(extractedText.getPageCount());
        document.setLanguage("vi");
        document.setExtractionStatus(cleanedText.isBlank() ? ExtractionStatus.FAILED : ExtractionStatus.CLEANED);
        document.setExtractionError(cleanedText.isBlank() ? ErrorCode.MATERIAL_TEXT_EMPTY.getMessage() : null);

        return extractedDocumentRepository.save(document);
    }

    private void saveChunks(UploadedMaterial material, ExtractedDocument document, List<String> chunks) {
        List<MaterialChunk> materialChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            MaterialChunk chunk = new MaterialChunk();
            chunk.setMaterial(material);
            chunk.setDocument(document);
            chunk.setWorkspace(material.getWorkspace());
            chunk.setChunkIndex(i);
            chunk.setContent(content);
            chunk.setTokenCount(estimateTokenCount(content));
            chunk.setSourceInfo("{\"materialId\":\"" + material.getMaterialId() + "\",\"chunkIndex\":" + i + "}");
            materialChunks.add(chunk);
        }
        materialChunkRepository.saveAll(materialChunks);
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : text.split("\\R{2,}|\\R")) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }

            if (current.length() + normalized.length() + 1 > MAX_CHUNK_LENGTH
                    && current.length() >= MIN_CHUNK_LENGTH) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }

            if (normalized.length() > MAX_CHUNK_LENGTH) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                flushLongParagraph(chunks, normalized);
            } else {
                current.append(normalized).append(System.lineSeparator());
            }
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private void flushLongParagraph(List<String> chunks, String paragraph) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            start = end;
        }
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.trim().split("\\s+").length);
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\u0000', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void failJob(
            MaterialProcessingJob job,
            UploadedMaterial material,
            ExtractedDocument document,
            ErrorCode errorCode,
            String message
    ) {
        String errorMessage = message == null || message.isBlank() ? errorCode.getMessage() : message;

        job.setStatus(ProcessingJobStatus.FAILED);
        job.setProgressPercent(100);
        job.setErrorCode(errorCode.name());
        job.setErrorMessage(errorMessage);
        job.setRetryable(false);
        job.setFinishedAt(Instant.now());

        material.setProcessingStatus(MaterialProcessingStatus.FAILED);
        material.setErrorMessage(errorMessage);

        if (document != null) {
            document.setExtractionStatus(ExtractionStatus.FAILED);
            document.setExtractionError(errorMessage);
        }

        log.warn("[MATERIAL] Failed job {}: {}", job.getJobId(), errorMessage);
        notificationService.notifyMaterialProcessingFailed(material, errorMessage);
    }
}
