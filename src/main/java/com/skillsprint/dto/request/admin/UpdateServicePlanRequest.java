package com.skillsprint.dto.request.admin;

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
public class UpdateServicePlanRequest {

    String planName;
    String description;
    List<String> benefits;
    BigDecimal monthlyPrice;
    String currency;
    Integer maxWorkspaces;
    Integer maxUploads;
    Integer aiGenerateLimit;
    Integer maxFileMb;
    Integer maxWorkspaceMb;
    Boolean publicVisible;
    Integer sortOrder;
}
