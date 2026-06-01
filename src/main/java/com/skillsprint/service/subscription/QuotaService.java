package com.skillsprint.service.subscription;

import com.skillsprint.dto.response.subscription.QuotaStatusResponse;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CalendarScheduleRunRepository;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuotaService {

    SubscriptionService subscriptionService;
    StudyWorkspaceRepository workspaceRepository;
    UploadedMaterialRepository uploadedMaterialRepository;
    LearningStructureVersionRepository learningStructureVersionRepository;
    RoadmapRepository roadmapRepository;
    CalendarScheduleRunRepository calendarScheduleRunRepository;

    @Transactional(readOnly = true)
    public QuotaStatusResponse getQuotaStatus(String userId) {
        ServicePlan plan = subscriptionService.getCurrentPlan(userId);

        long usedWorkspaces = countUsedWorkspaces(userId);
        long usedUploads = countUsedUploads(userId);
        long usedAiGenerate = countUsedAiGenerate(userId);
        long usedStorageBytes = safeLong(uploadedMaterialRepository.sumFileSizeByUserId(userId));

        int maxWorkspaces = valueOrDefault(plan.getMaxWorkspaces(), 1);
        int maxUploads = valueOrDefault(plan.getMaxUploads(), 5);
        int aiGenerateLimit = valueOrDefault(plan.getAiParsingLimit(), 5);
        int maxFileMb = valueOrDefault(plan.getMaxFileMb(), 20);
        int maxWorkspaceMb = valueOrDefault(plan.getMaxWorkspaceMb(), 100);

        double usedStorageMb = bytesToMb(usedStorageBytes);

        return QuotaStatusResponse.builder()
                .plan(plan.getPlanType())

                .maxWorkspaces(maxWorkspaces)
                .usedWorkspaces(usedWorkspaces)
                .remainingWorkspaces(remaining(maxWorkspaces, usedWorkspaces))

                .maxUploads(maxUploads)
                .usedUploads(usedUploads)
                .remainingUploads(remaining(maxUploads, usedUploads))

                .aiGenerateLimit(aiGenerateLimit)
                .usedAiGenerate(usedAiGenerate)
                .remainingAiGenerate(remaining(aiGenerateLimit, usedAiGenerate))

                .maxFileMb(maxFileMb)
                .maxWorkspaceMb(maxWorkspaceMb)
                .usedStorageBytes(usedStorageBytes)
                .usedStorageMb(usedStorageMb)
                .remainingStorageMb(Math.max(0, maxWorkspaceMb - usedStorageMb))
                .build();
    }

    @Transactional(readOnly = true)
    public void validateCanCreateWorkspace(String userId) {
        ServicePlan plan = subscriptionService.getCurrentPlan(userId);
        int maxWorkspaces = valueOrDefault(plan.getMaxWorkspaces(), 1);

        if (countUsedWorkspaces(userId) >= maxWorkspaces) {
            throw new AppException(ErrorCode.QUOTA_WORKSPACE_LIMIT_EXCEEDED);
        }
    }

    @Transactional(readOnly = true)
    public void validateCanStartMaterialUpload(String userId) {
        ServicePlan plan = subscriptionService.getCurrentPlan(userId);
        int maxUploads = valueOrDefault(plan.getMaxUploads(), 5);

        if (countUsedUploads(userId) >= maxUploads) {
            throw new AppException(ErrorCode.QUOTA_UPLOAD_LIMIT_EXCEEDED);
        }
    }

    @Transactional(readOnly = true)
    public void validateCanConfirmMaterialUpload(String userId, UUID workspaceId, long newFileSizeBytes) {
        ServicePlan plan = subscriptionService.getCurrentPlan(userId);

        int maxFileMb = valueOrDefault(plan.getMaxFileMb(), 20);
        int maxWorkspaceMb = valueOrDefault(plan.getMaxWorkspaceMb(), 100);

        if (newFileSizeBytes > mbToBytes(maxFileMb)) {
            throw new AppException(
                    ErrorCode.QUOTA_FILE_SIZE_LIMIT_EXCEEDED,
                    "File vượt quá giới hạn " + maxFileMb + "MB của gói hiện tại"
            );
        }

        long currentWorkspaceBytes = safeLong(
                uploadedMaterialRepository.sumFileSizeByWorkspaceIdAndUserId(workspaceId, userId)
        );

        if (currentWorkspaceBytes + newFileSizeBytes > mbToBytes(maxWorkspaceMb)) {
            throw new AppException(
                    ErrorCode.QUOTA_STORAGE_LIMIT_EXCEEDED,
                    "Workspace vượt quá giới hạn " + maxWorkspaceMb + "MB của gói hiện tại"
            );
        }
    }

    @Transactional(readOnly = true)
    public void validateCanGenerateAi(String userId) {
        ServicePlan plan = subscriptionService.getCurrentPlan(userId);
        int aiGenerateLimit = valueOrDefault(plan.getAiParsingLimit(), 5);

        if (countUsedAiGenerate(userId) >= aiGenerateLimit) {
            throw new AppException(ErrorCode.QUOTA_AI_GENERATE_LIMIT_EXCEEDED);
        }
    }

    private long countUsedWorkspaces(String userId) {
        return workspaceRepository.countByUserUserIdAndStatusNot(userId, WorkspaceStatus.DELETED);
    }

    private long countUsedUploads(String userId) {
        return uploadedMaterialRepository.countByUserUserId(userId);
    }

    private long countUsedAiGenerate(String userId) {
        long learningStructureCount = learningStructureVersionRepository.countByUserId(userId);
        long roadmapCount = roadmapRepository.countByUserUserId(userId);
        long calendarGenerateCount = calendarScheduleRunRepository.countByUserUserId(userId);
        return learningStructureCount + roadmapCount + calendarGenerateCount;
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private long remaining(int max, long used) {
        return Math.max(0, max - used);
    }

    private long mbToBytes(long mb) {
        return mb * 1024 * 1024;
    }

    private double bytesToMb(long bytes) {
        return Math.round((bytes / 1024.0 / 1024.0) * 100.0) / 100.0;
    }
}
