package com.skillsprint.dto.request.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceDisputeReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMarketplaceDisputeRequest {

    @NotNull(message = "Thiếu giao dịch cần yêu cầu hoàn tiền")
    private UUID saleId;

    @NotNull(message = "Thiếu lý do yêu cầu hoàn tiền")
    private MarketplaceDisputeReason reason;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;
}
