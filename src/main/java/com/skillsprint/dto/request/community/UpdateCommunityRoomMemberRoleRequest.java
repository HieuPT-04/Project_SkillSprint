package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.CommunityRoomRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCommunityRoomMemberRoleRequest {

    @NotNull(message = "Vai trò thành viên là bắt buộc")
    private CommunityRoomRole role;
}
