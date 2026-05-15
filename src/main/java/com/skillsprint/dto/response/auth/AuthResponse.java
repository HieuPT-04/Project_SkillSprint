package com.skillsprint.dto.response.auth;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthResponse {

    String accessToken;
    String idToken;
    String refreshToken;
    Integer expiresIn;
    String tokenType;
}
