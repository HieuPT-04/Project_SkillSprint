package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteCreatorPayoutRequest {

    @NotBlank
    @Size(max = 200)
    String externalTransferReference;

    @NotNull
    @Positive
    BigDecimal paidVndAmount;

    @Size(max = 5_000)
    String notes;
}
