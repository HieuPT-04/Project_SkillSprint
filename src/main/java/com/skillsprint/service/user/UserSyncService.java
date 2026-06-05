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
import jakarta.persistence.EntityManager;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSyncService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    UserRoleRepository userRoleRepository;
    EntityManager entityManager;

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
        if (existingUser.isEmpty()) {
            Optional<User> userWithSameEmail = userRepository.findByEmail(email);
            if (userWithSameEmail.isPresent()) {
                User oldUser = userWithSameEmail.get();
                String oldUserId = oldUser.getUserId();

                // 1. Temporarily change the email of the old user to avoid unique constraint violation on email
                oldUser.setEmail(email + "_old_" + System.currentTimeMillis());
                userRepository.saveAndFlush(oldUser);

                // 2. Create the new user with the new userId and the correct email
                User newUser = new User();
                newUser.setUserId(userId);
                newUser.setEmail(email);
                newUser.setEmailVerified(emailVerified);
                newUser.setFullName(fullName);
                newUser.setLastLoginAt(Instant.now());
                userRepository.saveAndFlush(newUser);

                // 3. Update all referencing tables to point to the new userId
                String[] tables = {
                    "user_roles", "study_workspaces", "uploaded_materials", "notifications",
                    "business_activity_logs", "subscriptions", "payment_transactions",
                    "pomodoro_sessions", "roadmaps", "roadmap_progress_logs", "study_sessions",
                    "calendar_tasks", "calendar_schedule_runs", "material_processing_jobs"
                };

                for (String table : tables) {
                    entityManager.createNativeQuery("UPDATE " + table + " SET user_id = :newId WHERE user_id = :oldId")
                            .setParameter("newId", userId)
                            .setParameter("oldId", oldUserId)
                            .executeUpdate();
                }

                // Update granted_by in user_roles specifically
                entityManager.createNativeQuery("UPDATE user_roles SET granted_by = :newId WHERE granted_by = :oldId")
                        .setParameter("newId", userId)
                        .setParameter("oldId", oldUserId)
                        .executeUpdate();

                // 4. Delete the old user
                userRepository.delete(oldUser);
                userRepository.flush();
            }
        }
        User user = userRepository.findById(userId).orElseGet(User::new);

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
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        userRoleRepository.deleteByUserUserIdAndWorkspaceIsNull(user.getUserId());

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);
    }
}
