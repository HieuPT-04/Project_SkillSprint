package com.skillsprint.dto.request.admin;

import com.skillsprint.enums.plan.ServicePlanType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateServicePlanRequest {

    @NotBlank
    String planName;

    String description;
    List<String> benefits;

    // Each ServicePlanType is unique per plan (DB constraint); required so it is never persisted null.
    @NotNull
    ServicePlanType planType;

    @NotNull
    @DecimalMin("0.0")
    BigDecimal monthlyPrice;

    String currency;

    Integer maxWorkspaces;
    Integer maxUploads;
    Integer aiGenerateLimit;
    Integer maxFileMb;
    Integer maxWorkspaceMb;
    Boolean active;
    Boolean publicVisible;
    Integer sortOrder;

    @Valid
    List<UpdatePlanFeaturesRequest.FeatureToggle> features;
}
