package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.PostCommentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePostCommentStatusRequest {

    @NotNull(message = "Trạng thái bình luận là bắt buộc")
    private PostCommentStatus status;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String adminNote;
}
