package com.skillsprint.dto.request.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMarketplaceContentReportRequest {

    @NotNull(message = "Thiếu phiên bản Quiz Pack cần báo cáo")
    private UUID packVersionId;

    @NotNull(message = "Thiếu loại nội dung báo cáo")
    private MarketplaceReportTargetType targetType;

    @Size(max = 200, message = "Mã nội dung báo cáo không hợp lệ")
    private String targetRef;

    @NotNull(message = "Thiếu lý do báo cáo")
    private MarketplaceReportCategory category;

    @Size(max = 2000, message = "Mô tả báo cáo tối đa 2000 ký tự")
    private String description;

    @Size(max = 512, message = "Khóa ảnh minh chứng không hợp lệ")
    private String evidenceObjectKey;
}
