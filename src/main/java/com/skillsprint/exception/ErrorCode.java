package com.skillsprint.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Không tìm thấy dữ liệu", HttpStatus.NOT_FOUND),
    FORBIDDEN("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
