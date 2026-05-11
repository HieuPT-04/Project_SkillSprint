package com.skillsprint.common.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorResponse {

    private boolean success;
    private String code;
    private String message;
    private String path;
    private List<FieldErrorDetail> errors;
    private Instant timestamp;

    public ErrorResponse(String code, String message, String path, List<FieldErrorDetail> errors) {
        this.success = false;
        this.code = code;
        this.message = message;
        this.path = path;
        this.errors = errors;
        this.timestamp = Instant.now();
    }

    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, path, List.of());
    }

    public static ErrorResponse validation(String message, String path, List<FieldErrorDetail> errors) {
        return new ErrorResponse("VALIDATION_ERROR", message, path, errors);
    }
}
