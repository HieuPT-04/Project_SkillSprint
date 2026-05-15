package com.skillsprint.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationCodeRequest(
        @NotBlank
        @Email
        String email
) {
}
