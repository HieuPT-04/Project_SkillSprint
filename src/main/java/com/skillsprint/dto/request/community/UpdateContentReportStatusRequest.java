package com.skillsprint.dto.request.community;

import com.skillsprint.enums.community.ContentReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateContentReportStatusRequest {

    @NotNull(message = "Trạng thái report là bắt buộc")
    private ContentReportStatus status;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String adminNote;
}
