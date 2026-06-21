package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendCommunityChatMessageRequest {

    @Size(max = 2000, message = "Tin nhắn tối đa 2000 ký tự")
    private String content;
}
