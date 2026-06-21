package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePostCommentRequest {

    @Size(max = 1000, message = "Bình luận tối đa 1000 ký tự")
    private String content;
}
