package com.skillsprint.service.user;

import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserRole;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.UserMapper;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserRoleRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    UserRepository userRepository;
    UserRoleRepository userRoleRepository;
    UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<User> users = userRepository.searchUsers(normalizeSearch(search), pageable);
        Map<String, List<String>> rolesByUserId = getRolesByUserId(users.getContent());

        Page<AdminUserResponse> response = users.map(user -> userMapper.toAdminUserResponse(
                user,
                rolesByUserId.getOrDefault(user.getUserId(), List.of())
        ));

        return PageResponse.from(response);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng"));

        List<String> roles = userRoleRepository.findByUserUserIdAndWorkspaceIsNull(userId)
                .stream()
                .map(this::toRoleName)
                .toList();

        return userMapper.toAdminUserResponse(user, roles);
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
}
