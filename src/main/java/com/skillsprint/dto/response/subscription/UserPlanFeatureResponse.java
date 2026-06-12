package com.skillsprint.dto.response.subscription;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPlanFeatureResponse {

    String featureKey;
    String featureName;
}
