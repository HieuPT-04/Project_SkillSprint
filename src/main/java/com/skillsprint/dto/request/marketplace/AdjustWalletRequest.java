package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class AdjustWalletRequest {
    @NotNull(message = "amount không được để trống")
    private Integer amount;
}
