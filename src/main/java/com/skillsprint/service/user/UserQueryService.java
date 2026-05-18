package com.skillsprint.service.user;

import com.skillsprint.dto.request.user.UpdateMeRequest;
import com.skillsprint.dto.response.user.MeResponse;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserRole;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.UserMapper;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserRoleRepository;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserQueryService {

    UserRepository userRepository;
    UserRoleRepository userRoleRepository;
    UserMapper userMapper;

    @Transactional(readOnly = true)
    public MeResponse getMe(String userId) {
        User user = findUser(userId);

        return userMapper.toMeResponse(user, getGlobalRoles(userId));
    }

    @Transactional
    public MeResponse updateMe(String userId, UpdateMeRequest request) {
        User user = findUser(userId);

        if (request.getFullName() != null) {
            String fullName = request.getFullName().trim();
            if (fullName.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Tên người dùng không được để trống");
            }
            user.setFullName(fullName);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toMeResponse(savedUser, getGlobalRoles(userId));
    }

    @Transactional
    public MeResponse updateAvatar(String userId, String avatarUrl) {
        User user = findUser(userId);
        user.setAvatarUrl(avatarUrl);

        User savedUser = userRepository.save(user);
        return userMapper.toMeResponse(savedUser, getGlobalRoles(userId));
    }

    private User findUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));
        if (UserStatus.DISABLED.equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private List<String> getGlobalRoles(String userId) {
        List<String> roles = userRoleRepository.findByUserUserIdAndWorkspaceIsNull(userId)
                .stream()
                .map(UserRole::getRole)
                .map(role -> role.getRoleName().name())
                .toList();

        return roles;
    }
}
