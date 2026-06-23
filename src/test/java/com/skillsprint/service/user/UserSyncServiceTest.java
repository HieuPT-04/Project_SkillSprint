package com.skillsprint.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.Role;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserRole;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.RoleRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserRoleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    UserRoleRepository userRoleRepository;

    UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        userSyncService = new UserSyncService(userRepository, roleRepository, userRoleRepository);
    }

    @Test
    void syncLearnerCreatesUserAndGlobalLearnerRole() {
        Role learnerRole = role(RoleName.LEARNER);
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByRoleName(RoleName.LEARNER)).thenReturn(Optional.of(learnerRole));

        User result = userSyncService.syncLearner(
                "user-1",
                "learner@example.com",
                true,
                "Learner"
        );

        assertEquals("user-1", result.getUserId());
        assertEquals("learner@example.com", result.getEmail());
        assertEquals("Learner", result.getFullName());
        assertNotNull(result.getLastLoginAt());
        verify(userRoleRepository).deleteByUserUserIdAndWorkspaceIsNull("user-1");

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertEquals(result, captor.getValue().getUser());
        assertEquals(learnerRole, captor.getValue().getRole());
    }

    @Test
    void syncWithRolePreservesExistingFullNameAndUpdatesCognitoFields() {
        User existing = new User();
        existing.setUserId("user-1");
        existing.setEmail("old@example.com");
        existing.setFullName("Local name");
        Role adminRole = role(RoleName.ADMIN);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(roleRepository.findByRoleName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));

        User result = userSyncService.syncWithRole(
                "user-1",
                "new@example.com",
                true,
                "Cognito name",
                RoleName.ADMIN
        );

        assertEquals("new@example.com", result.getEmail());
        assertEquals("Local name", result.getFullName());
        assertNotNull(result.getLastLoginAt());

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertEquals(adminRole, captor.getValue().getRole());
    }

    @Test
    void syncWithRoleRejectsMissingRoleBeforeReplacingGlobalRole() {
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByRoleName(RoleName.LEARNER)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> userSyncService.syncLearner(
                        "user-1",
                        "learner@example.com",
                        false,
                        "Learner"
                )
        );

        assertEquals(ErrorCode.ROLE_NOT_FOUND, exception.getErrorCode());
        verify(userRoleRepository, never()).deleteByUserUserIdAndWorkspaceIsNull("user-1");
        verify(userRoleRepository, never()).save(any());
    }

    private Role role(RoleName roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        return role;
    }
}
