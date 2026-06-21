package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MuteCommunityRoomMemberRequest {

    @Min(value = 1, message = "Thời gian mute tối thiểu 1 phút")
    private Integer minutes = 30;
}
