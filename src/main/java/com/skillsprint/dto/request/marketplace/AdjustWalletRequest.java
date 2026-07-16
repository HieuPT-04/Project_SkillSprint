package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class AdjustWalletRequest {
    @NotNull(message = "amount không được để trống")
    private Integer amount;

    @NotBlank(message = "reason không được để trống")
    @Size(max = 500, message = "reason tối đa 500 ký tự")
    private String reason;
}
