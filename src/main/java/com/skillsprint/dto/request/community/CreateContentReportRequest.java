package com.skillsprint.dto.request.community;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateContentReportRequest {

    @Size(max = 1000, message = "Lý do báo cáo tối đa 1000 ký tự")
    private String reason;
}
