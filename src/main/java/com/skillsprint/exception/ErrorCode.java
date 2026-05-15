package com.skillsprint.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    UNAUTHORIZED("Bạn cần đăng nhập hoặc cung cấp token hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Email hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_CONFIRMED("Tài khoản chưa xác thực email", HttpStatus.FORBIDDEN),
    INVALID_CONFIRMATION_CODE("Mã xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    USER_ALREADY_EXISTS("Email này đã được đăng ký", HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND("Không tìm thấy dữ liệu", HttpStatus.NOT_FOUND),
    FORBIDDEN("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    COGNITO_ERROR("Không thể xử lý yêu cầu xác thực", HttpStatus.BAD_GATEWAY),
    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;
}
