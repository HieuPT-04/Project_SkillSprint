package com.skillsprint.dto.request.admin;

import com.skillsprint.enums.auth.UserStatus;
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
public class UpdateUserStatusRequest {

    @NotNull
    UserStatus status;
}
