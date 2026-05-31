package com.skillsprint.dto.request.subscription;

import com.skillsprint.enums.plan.ServicePlanType;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class    UpdateSubscriptionPlanRequest {

    @NotNull
    ServicePlanType planType;
}