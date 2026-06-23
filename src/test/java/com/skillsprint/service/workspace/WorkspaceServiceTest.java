package com.skillsprint.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.skillsprint.service.subscription.QuotaService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    StudyWorkspaceRepository workspaceRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    WorkspaceMapper workspaceMapper;

    @Mock
    QuotaService quotaService;

    WorkspaceService workspaceService;
    User user;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
                workspaceRepository,
                userRepository,
                workspaceMapper,
                quotaService
        );
        user = user("user-1");
    }

    @Test
    void createWorkspaceRejectsMissingUserBeforeCheckingQuota() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> workspaceService.createWorkspace("missing", createRequest("Java", "Learn Java"))
        );

        assertEquals(ErrorCode.USER_PROFILE_NOT_FOUND, exception.getErrorCode());
        verify(quotaService, never()).validateCanCreateWorkspace(any());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void createWorkspaceNormalizesTextChecksQuotaAndReturnsMappedResponse() {
        WorkspaceResponse expected = WorkspaceResponse.builder()
                .workspaceId(UUID.randomUUID())
                .name("Java")
                .description("Learn Java")
                .status(WorkspaceStatus.ACTIVE)
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(workspaceRepository.save(any(StudyWorkspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceMapper.toWorkspaceResponse(any(StudyWorkspace.class))).thenReturn(expected);

        WorkspaceResponse response = workspaceService.createWorkspace(
                "user-1",
                createRequest("  Java  ", "  Learn Java  ")
        );

        assertSame(expected, response);
        verify(quotaService).validateCanCreateWorkspace("user-1");

        ArgumentCaptor<StudyWorkspace> captor = ArgumentCaptor.forClass(StudyWorkspace.class);
        verify(workspaceRepository).save(captor.capture());
        StudyWorkspace saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals("Java", saved.getName());
        assertEquals("Learn Java", saved.getDescription());
        assertEquals(WorkspaceStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void createWorkspaceRejectsBlankNameAfterTrim() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        AppException exception = assertThrows(
                AppException.class,
                () -> workspaceService.createWorkspace("user-1", createRequest("   ", "desc"))
        );

        assertEquals(ErrorCode.WORKSPACE_NAME_REQUIRED, exception.getErrorCode());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void getMyWorkspacesReturnsNonDeletedWorkspacesNewestFirst() {
        StudyWorkspace first = workspace("First", WorkspaceStatus.ACTIVE);
        StudyWorkspace second = workspace("Second", WorkspaceStatus.ARCHIVED);
        WorkspaceResponse firstResponse = WorkspaceResponse.builder().workspaceId(first.getWorkspaceId()).build();
        WorkspaceResponse secondResponse = WorkspaceResponse.builder().workspaceId(second.getWorkspaceId()).build();
        when(workspaceRepository.findByUserUserIdAndStatusNotOrderByCreatedAtDesc("user-1", WorkspaceStatus.DELETED))
                .thenReturn(List.of(first, second));
        when(workspaceMapper.toWorkspaceResponse(first)).thenReturn(firstResponse);
        when(workspaceMapper.toWorkspaceResponse(second)).thenReturn(secondResponse);

        List<WorkspaceResponse> response = workspaceService.getMyWorkspaces("user-1");

        assertEquals(List.of(firstResponse, secondResponse), response);
    }

    @Test
    void getWorkspaceRejectsMissingOrDeletedWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> workspaceService.getWorkspace("user-1", workspaceId)
        );

        assertEquals(ErrorCode.WORKSPACE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateWorkspaceAppliesOnlyProvidedFieldsAndNormalizesBlankDescriptionToNull() {
        UUID workspaceId = UUID.randomUUID();
        StudyWorkspace workspace = workspace("Old", WorkspaceStatus.ACTIVE);
        workspace.setWorkspaceId(workspaceId);
        WorkspaceResponse expected = WorkspaceResponse.builder()
                .workspaceId(workspaceId)
                .name("New")
                .status(WorkspaceStatus.ARCHIVED)
                .build();
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(workspace)).thenReturn(workspace);
        when(workspaceMapper.toWorkspaceResponse(workspace)).thenReturn(expected);

        UpdateWorkspaceRequest request = new UpdateWorkspaceRequest();
        request.setName("  New  ");
        request.setDescription("   ");
        request.setStatus(WorkspaceStatus.ARCHIVED);

        WorkspaceResponse response = workspaceService.updateWorkspace("user-1", workspaceId, request);

        assertSame(expected, response);
        assertEquals("New", workspace.getName());
        assertNull(workspace.getDescription());
        assertEquals(WorkspaceStatus.ARCHIVED, workspace.getStatus());
    }

    @Test
    void updateWorkspaceRejectsBlankProvidedName() {
        UUID workspaceId = UUID.randomUUID();
        StudyWorkspace workspace = workspace("Old", WorkspaceStatus.ACTIVE);
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));

        UpdateWorkspaceRequest request = new UpdateWorkspaceRequest();
        request.setName("   ");

        AppException exception = assertThrows(
                AppException.class,
                () -> workspaceService.updateWorkspace("user-1", workspaceId, request)
        );

        assertEquals(ErrorCode.WORKSPACE_NAME_REQUIRED, exception.getErrorCode());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void deleteWorkspaceSoftDeletesOwnedWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        StudyWorkspace workspace = workspace("Old", WorkspaceStatus.ACTIVE);
        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                workspaceId,
                "user-1",
                WorkspaceStatus.DELETED
        )).thenReturn(Optional.of(workspace));

        workspaceService.deleteWorkspace("user-1", workspaceId);

        assertEquals(WorkspaceStatus.DELETED, workspace.getStatus());
        verify(workspaceRepository).save(workspace);
    }

    private CreateWorkspaceRequest createRequest(String name, String description) {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private StudyWorkspace workspace(String name, WorkspaceStatus status) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        workspace.setUser(user);
        workspace.setName(name);
        workspace.setDescription("Description");
        workspace.setStatus(status);
        return workspace;
    }

    private User user(String userId) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }
}
