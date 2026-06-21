package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HideCommunityChatMessageRequest {

    private Boolean hidden = true;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String adminNote;
}
