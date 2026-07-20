package com.skillsprint.dto.request.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMarketplaceContentReportStatusRequest {

    @NotNull(message = "Thiếu trạng thái báo cáo")
    private MarketplaceReportStatus status;

    @Size(max = 2000, message = "Ghi chú xử lý tối đa 2000 ký tự")
    private String resolutionNote;
}
