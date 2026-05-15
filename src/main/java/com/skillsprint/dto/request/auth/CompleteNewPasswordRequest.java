package com.skillsprint.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteNewPasswordRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String newPassword,

        @NotBlank
        String session
) {
}
