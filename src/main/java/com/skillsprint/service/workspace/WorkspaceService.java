package com.skillsprint.service.workspace;

import com.skillsprint.dto.request.workspace.CreateWorkspaceRequest;
import com.skillsprint.dto.request.workspace.UpdateWorkspaceRequest;
import com.skillsprint.dto.response.workspace.WorkspaceResponse;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.WorkspaceMapper;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkspaceService {

    StudyWorkspaceRepository workspaceRepository;
    UserRepository userRepository;
    WorkspaceMapper workspaceMapper;

    @Transactional
    public WorkspaceResponse createWorkspace(String userId, CreateWorkspaceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(user);
        workspace.setName(normalizeRequiredText(request.getName(), ErrorCode.WORKSPACE_NAME_REQUIRED));
        workspace.setDescription(normalizeOptionalText(request.getDescription()));
        workspace.setStatus(WorkspaceStatus.ACTIVE);

        return workspaceMapper.toWorkspaceResponse(workspaceRepository.save(workspace));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getMyWorkspaces(String userId) {
        return workspaceRepository.findByUserUserIdAndStatusNotOrderByCreatedAtDesc(userId, WorkspaceStatus.DELETED)
                .stream()
                .map(workspaceMapper::toWorkspaceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(String userId, UUID workspaceId) {
        return workspaceMapper.toWorkspaceResponse(findOwnedWorkspace(userId, workspaceId));
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(String userId, UUID workspaceId, UpdateWorkspaceRequest request) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);

        if (request.getName() != null) {
            workspace.setName(normalizeRequiredText(request.getName(), ErrorCode.WORKSPACE_NAME_REQUIRED));
        }
        if (request.getDescription() != null) {
            workspace.setDescription(normalizeOptionalText(request.getDescription()));
        }
        if (request.getStatus() != null) {
            workspace.setStatus(request.getStatus());
        }

        return workspaceMapper.toWorkspaceResponse(workspaceRepository.save(workspace));
    }

    @Transactional
    public void deleteWorkspace(String userId, UUID workspaceId) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        workspace.setStatus(WorkspaceStatus.DELETED);
        workspaceRepository.save(workspace);
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private String normalizeRequiredText(String value, ErrorCode errorCode) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new AppException(errorCode);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
