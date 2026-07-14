package com.skillsprint.dto.request.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewMarketplaceItemRequest {

    @NotNull(message = "Quyết định kiểm duyệt không được để trống")
    private MarketplaceItemStatus status;

    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String reviewNote;
}
