package com.skillsprint.dto.request.admin;

import com.skillsprint.enums.auth.RoleName;
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
public class UpdateUserRoleRequest {

    @NotNull
    RoleName role;
}
