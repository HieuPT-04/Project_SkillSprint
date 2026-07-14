package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateMarketplaceItemRequest {

    @NotNull(message = "workspaceId không được để trống")
    private UUID workspaceId;

    @NotBlank(message = "Tên Quiz Pack không được để trống")
    @Size(max = 500, message = "Tên Quiz Pack tối đa 500 ký tự")
    private String title;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @NotBlank(message = "Môn học không được để trống")
    @Size(max = 100, message = "Môn học tối đa 100 ký tự")
    private String subject;

    @NotNull(message = "Giá coin không được để trống")
    @Min(value = 0, message = "Giá coin không được âm")
    private Integer priceCoins;
}
