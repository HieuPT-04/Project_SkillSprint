package com.skillsprint.dto.request.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Admin review decision. A decision note is mandatory for APPROVED/REJECTED. */
@Getter
@Setter
public class DecideMarketplaceDisputeRequest {

    @NotNull(message = "Thiếu trạng thái quyết định")
    private MarketplaceDisputeStatus status;

    @Size(max = 2000, message = "Ghi chú quyết định tối đa 2000 ký tự")
    private String decisionNote;
}
