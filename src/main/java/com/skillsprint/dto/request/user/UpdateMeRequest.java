package com.skillsprint.dto.request.user;

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
public class UpdateMeRequest {

    @Size(max = 255)
    String fullName;

    @Size(max = 2048)
    String avatarUrl;
}
