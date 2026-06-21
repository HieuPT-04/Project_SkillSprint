package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommunityRoomInviteRequest {

    @NotBlank(message = "User ID người được mời là bắt buộc")
    private String inviteeUserId;
}
