package com.skillsprint.dto.response.subscription;

import com.skillsprint.enums.plan.ServicePlanType;
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
public class ServicePlanResponse {

    UUID planId;
    String planName;
    String description;
    List<String> benefits;
    ServicePlanType planType;
    BigDecimal monthlyPrice;
    String currency;

    ServicePlanQuotaResponse quotas;

    boolean active;
    Boolean publicVisible;
    Integer sortOrder;
    List<ServicePlanFeatureResponse> features;
}
