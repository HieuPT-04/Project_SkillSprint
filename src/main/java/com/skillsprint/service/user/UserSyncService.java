package com.skillsprint.service.user;

import com.skillsprint.entity.Role;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserRole;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.RoleRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserRoleRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSyncService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    UserRoleRepository userRoleRepository;

    @Transactional
    public User syncLearner(String userId, String email, boolean emailVerified, String fullName) {
        return syncWithRole(userId, email, emailVerified, fullName, RoleName.LEARNER);
    }

    @Transactional
    public User syncWithRole(
            String userId,
            String email,
            boolean emailVerified,
            String fullName,
            RoleName roleName
    ) {
        Optional<User> existingUser = userRepository.findById(userId);
        User user = existingUser.orElseGet(User::new);

        user.setUserId(userId);
        user.setEmail(email);
        user.setEmailVerified(emailVerified);
        if (existingUser.isEmpty()) {
            user.setFullName(fullName);
        }
        user.setLastLoginAt(Instant.now());

        User savedUser = userRepository.save(user);
        replaceGlobalRole(savedUser, roleName);
        return savedUser;
    }

    private void replaceGlobalRole(User user, RoleName roleName) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Role " + roleName + " chưa được seed"));

        userRoleRepository.deleteByUserUserIdAndWorkspaceIsNull(user.getUserId());

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);
    }
}
