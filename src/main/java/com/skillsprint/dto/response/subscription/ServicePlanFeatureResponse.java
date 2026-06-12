package com.skillsprint.dto.response.subscription;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServicePlanFeatureResponse {

    UUID featureId;
    String featureKey;
    String featureName;
    String description;
    boolean active;
    boolean enabled;
}
