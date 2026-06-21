package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.CommunityPinItemType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommunityPinRequest {

    @NotNull(message = "Loại ghim là bắt buộc")
    private CommunityPinItemType itemType;

    @Size(max = 255, message = "Tiêu đề ghim tối đa 255 ký tự")
    private String title;

    @Size(max = 5000, message = "Nội dung ghim tối đa 5000 ký tự")
    private String content;

    private UUID messageId;
}
