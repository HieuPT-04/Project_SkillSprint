package com.skillsprint.dto.response.subscription;

import com.skillsprint.enums.plan.ServicePlanType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuotaStatusResponse {

    ServicePlanType plan;

    Integer maxWorkspaces;
    Long usedWorkspaces;
    Long remainingWorkspaces;

    Integer maxUploads;
    Long usedUploads;
    Long remainingUploads;

    Integer maxCommunityRooms;
    Long usedCommunityRooms;
    Long remainingCommunityRooms;

    Integer aiGenerateLimit;
    Long usedAiGenerate;
    Long remainingAiGenerate;

    Integer maxFileMb;
    Integer maxWorkspaceMb;
    Long usedStorageBytes;
    Double usedStorageMb;
    Double remainingStorageMb;
}
