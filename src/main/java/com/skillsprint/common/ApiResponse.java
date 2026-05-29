package com.skillsprint.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skillsprint.exception.ErrorCode;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApiResponse<T> {

    boolean success;
    Integer code;
    String message;
    T data;
    String path;
    List<FieldErrorDetail> errors;

    public static <T> ApiResponse<T> success(T data) {
        return success("Thành công", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, HttpStatus.OK.value(), message, data, null, null);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(true, HttpStatus.CREATED.value(), message, data, null, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String path) {
        return new ApiResponse<>(
                false,
                errorCode.getStatus().value(),
                errorCode.getMessage(),
                null,
                path,
                null
        );
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String path) {
        return new ApiResponse<>(
                false,
                errorCode.getStatus().value(),
                message,
                null,
                path,
                null
        );
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String path, List<FieldErrorDetail> errors) {
        return new ApiResponse<>(
                false,
                errorCode.getStatus().value(),
                errorCode.getMessage(),
                null,
                path,
                errors
        );
    }
}
