package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.CommunityPostStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCommunityPostStatusRequest {

    @NotNull(message = "Trạng thái bài viết là bắt buộc")
    private CommunityPostStatus status;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String adminNote;
}
