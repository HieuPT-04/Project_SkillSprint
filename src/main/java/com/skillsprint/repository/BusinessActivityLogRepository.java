package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.enums.log.BusinessActionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessActivityLogRepository extends JpaRepository<BusinessActivityLog, UUID> {

    List<BusinessActivityLog> findByUserUserIdOrderByCreatedAtDesc(String userId);

    List<BusinessActivityLog> findByWorkspaceWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<BusinessActivityLog> findByActionTypeOrderByCreatedAtDesc(BusinessActionType actionType);
}
