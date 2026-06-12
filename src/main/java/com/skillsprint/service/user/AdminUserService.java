package com.skillsprint.service.user;

import com.skillsprint.configuration.cognito.CognitoProperties;
import com.skillsprint.dto.request.admin.UpdateUserRoleRequest;
import com.skillsprint.dto.request.admin.UpdateUserStatusRequest;
import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.entity.Role;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserRole;
import com.skillsprint.entity.Subscription;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.UserMapper;
import com.skillsprint.repository.RoleRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserRoleRepository;
import com.skillsprint.repository.SubscriptionRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    UserRepository userRepository;
    UserRoleRepository userRoleRepository;
    RoleRepository roleRepository;
    SubscriptionRepository subscriptionRepository; // Đã thêm mới repo này
    UserMapper userMapper;
    CognitoIdentityProviderClient cognitoClient;
    CognitoProperties cognitoProperties;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        String normalizedSearch = normalizeSearch(search);
        Page<User> users = normalizedSearch == null
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        normalizedSearch,
                        normalizedSearch,
                        pageable
                );
        
        Map<String, List<String>> rolesByUserId = getRolesByUserId(users.getContent());

        // --- Bắt đầu đoạn tối ưu Batch-fetch lấy Subscription tránh N+1 Query ---
        List<String> userIds = users.getContent().stream()
                .map(User::getUserId)
                .toList();

        Map<String, Subscription> subscriptionsByUserId = userIds.isEmpty()
                ? Collections.emptyMap()
                : subscriptionRepository.findByUserUserIdIn(userIds)
                        .stream()
                        .filter(sub -> sub.getUser() != null && sub.getUser().getUserId() != null)
                        .collect(Collectors.toMap(
                                sub -> sub.getUser().getUserId(),
                                Function.identity(),
                                (s1, s2) -> s1.getCreatedAt().isAfter(s2.getCreatedAt()) ? s1 : s2
                        ));
        // --- Kết thúc đoạn xử lý Subscription ---

        Page<AdminUserResponse> response = users.map(user -> userMapper.toAdminUserResponse(
                user,
                rolesByUserId.getOrDefault(user.getUserId(), List.of()),
                subscriptionsByUserId.get(user.getUserId()) // Đã truyền thêm dữ liệu gói ở đây
        ));

        return PageResponse.from(response);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(String userId) {
        User user = findUser(userId);
        
        Subscription currentSubscription = subscriptionRepository
                .findAllByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElse(null);

        return userMapper.toAdminUserResponse(user, getGlobalRoles(userId), currentSubscription);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(String userId, UpdateUserStatusRequest request) {
        User user = findUser(userId);
        if (!request.getStatus().canBeManagedByAdmin()) {
            throw new AppException(ErrorCode.INVALID_USER_STATUS);
        }

        user.setStatus(request.getStatus());

        User savedUser = userRepository.save(user);
        
        Subscription currentSubscription = subscriptionRepository
                .findAllByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElse(null);
                
        return userMapper.toAdminUserResponse(savedUser, getGlobalRoles(userId), currentSubscription);
    }

    @Transactional
    public AdminUserResponse updateUserRole(String userId, UpdateUserRoleRequest request) {
        User user = findUser(userId);
        Role role = roleRepository.findByRoleName(request.getRole())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        syncCognitoGroup(user, request.getRole());

        userRoleRepository.deleteByUserUserIdAndWorkspaceIsNull(userId);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRoleRepository.save(userRole);

        Subscription currentSubscription = subscriptionRepository
                .findAllByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElse(null);

        return userMapper.toAdminUserResponse(user, List.of(request.getRole().name()), currentSubscription);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private List<String> getGlobalRoles(String userId) {
        return userRoleRepository.findByUserUserIdAndWorkspaceIsNull(userId)
                .stream()
                .map(this::toRoleName)
                .toList();
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private Map<String, List<String>> getRolesByUserId(Collection<User> users) {
        List<String> userIds = users.stream()
                .map(User::getUserId)
                .toList();

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRoleRepository.findByUserUserIdInAndWorkspaceIsNull(userIds)
                .stream()
                .collect(Collectors.groupingBy(
                        userRole -> userRole.getUser().getUserId(),
                        Collectors.mapping(this::toRoleName, Collectors.toList())
                ));
    }

    private String toRoleName(UserRole userRole) {
        return userRole.getRole().getRoleName().name();
    }

    private void syncCognitoGroup(User user, RoleName roleName) {
        try {
            for (RoleName candidate : RoleName.values()) {
                if (candidate.equals(roleName)) {
                    addUserToCognitoGroup(user.getEmail(), candidate);
                } else {
                    removeUserFromCognitoGroup(user.getEmail(), candidate);
                }
            }
        } catch (CognitoIdentityProviderException ex) {
            throw new AppException(ErrorCode.COGNITO_SERVICE_ERROR);
        }
    }

    private void addUserToCognitoGroup(String username, RoleName roleName) {
        cognitoClient.adminAddUserToGroup(
                AdminAddUserToGroupRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId())
                        .username(username)
                        .groupName(roleName.name())
                        .build()
        );
    }

    private void removeUserFromCognitoGroup(String username, RoleName roleName) {
        try {
            cognitoClient.adminRemoveUserFromGroup(
                    AdminRemoveUserFromGroupRequest.builder()
                            .userPoolId(cognitoProperties.userPoolId())
                            .username(username)
                            .groupName(roleName.name())
                            .build()
            );
        } catch (UserNotFoundException ex) {
            throw ex;
        } catch (CognitoIdentityProviderException ignored) {
            // Cognito may fail when the user is already absent from the group.
        }
    }
}