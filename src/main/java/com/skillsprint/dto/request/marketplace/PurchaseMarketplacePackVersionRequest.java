package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Request contract for the version-aware Coin checkout introduced in Plan 3B. */
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PurchaseMarketplacePackVersionRequest {

    @NotBlank(message = "Cần cung cấp khóa chống tạo giao dịch trùng")
    @Size(max = 100)
    String idempotencyKey;
}
