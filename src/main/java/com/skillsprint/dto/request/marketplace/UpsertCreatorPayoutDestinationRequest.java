package com.skillsprint.dto.request.marketplace;

import jakarta.validation.constraints.NotBlank;
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
public class UpsertCreatorPayoutDestinationRequest {

    @NotBlank
    @Size(max = 150)
    String bankName;

    @Size(max = 50)
    String bankCode;

    @NotBlank
    @Size(max = 200)
    String accountHolder;

    @NotBlank
    @Size(max = 512)
    String qrObjectKey;
}
