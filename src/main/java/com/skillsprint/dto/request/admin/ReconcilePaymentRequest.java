package com.skillsprint.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReconcilePaymentRequest {

    @NotBlank(message = "Mã giao dịch SePay không được để trống")
    String providerTransactionId;

    String providerReferenceCode;

    Instant paidAt;

    String note;
}
