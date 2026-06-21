package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.CommunityRoomStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCommunityRoomStatusRequest {

    @NotNull(message = "Trạng thái phòng là bắt buộc")
    private CommunityRoomStatus status;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String adminNote;
}
