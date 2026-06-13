package com.skillsprint.dto.response.subscription;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserServicePlanResponse {

    UUID planId;
    String planName;
    String description;
    List<String> benefits;
    BigDecimal monthlyPrice;
    String currency;
    ServicePlanQuotaResponse quotas;
}
