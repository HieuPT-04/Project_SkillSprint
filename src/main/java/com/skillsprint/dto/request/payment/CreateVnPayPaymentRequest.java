package com.skillsprint.dto.request.payment;

import com.skillsprint.enums.plan.ServicePlanType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class CreateVnPayPaymentRequest {

    @NotNull
    ServicePlanType planType;

    @Min(1)
    @Max(12)
    Integer months = 1;

    @Size(max = 20)
    String bankCode;

    @Size(max = 5)
    String locale = "vn";
}