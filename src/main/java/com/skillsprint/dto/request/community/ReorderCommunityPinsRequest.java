package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReorderCommunityPinsRequest {

    @NotEmpty(message = "Danh sách pin là bắt buộc")
    private List<UUID> pinIds;
}
