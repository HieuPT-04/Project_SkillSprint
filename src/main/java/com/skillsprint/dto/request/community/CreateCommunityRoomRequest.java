package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.CommunityRoomMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommunityRoomRequest {

    @Size(max = 150, message = "Tên phòng tối đa 150 ký tự")
    private String name;

    @Size(max = 2000, message = "Mô tả phòng tối đa 2000 ký tự")
    private String description;

    private CommunityRoomMode mode = CommunityRoomMode.PUBLIC;

    @Min(value = 2, message = "Phòng cần tối thiểu 2 thành viên")
    @Max(value = 500, message = "Phòng tối đa 500 thành viên")
    private Integer maxMembers = 50;
}
