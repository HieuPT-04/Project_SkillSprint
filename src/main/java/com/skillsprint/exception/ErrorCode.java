package com.skillsprint.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth / security
    UNAUTHORIZED("Bạn cần đăng nhập hoặc cung cấp token hợp lệ", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Email hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_CONFIRMED("Tài khoản chưa xác thực email", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("Tài khoản đã bị khóa", HttpStatus.FORBIDDEN),
    INVALID_CONFIRMATION_CODE("Mã xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    USER_ALREADY_EXISTS("Email này đã được đăng ký", HttpStatus.CONFLICT),

    // User / role
    USER_NOT_FOUND("Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USER_PROFILE_NOT_FOUND("Không tìm thấy hồ sơ người dùng", HttpStatus.NOT_FOUND),
    USER_FULL_NAME_REQUIRED("Tên người dùng không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_USER_STATUS("Chỉ hỗ trợ cập nhật trạng thái ACTIVE hoặc DISABLED", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND("Role chưa được seed", HttpStatus.NOT_FOUND),

    // Workspace
    WORKSPACE_NOT_FOUND("Không tìm thấy workspace", HttpStatus.NOT_FOUND),
    WORKSPACE_NAME_REQUIRED("Tên workspace không được để trống", HttpStatus.BAD_REQUEST),

    // Onboarding
    ONBOARDING_PROFILE_NOT_FOUND("Không tìm thấy onboarding profile", HttpStatus.NOT_FOUND),
    ONBOARDING_TARGET_GOAL_REQUIRED("Mục tiêu học tập không được để trống", HttpStatus.BAD_REQUEST),
    ONBOARDING_WRITE_FAILED("Không thể xử lý dữ liệu onboarding", HttpStatus.INTERNAL_SERVER_ERROR),
    ONBOARDING_READ_FAILED("Không thể đọc dữ liệu onboarding", HttpStatus.INTERNAL_SERVER_ERROR),

    // Avatar / S3
    INVALID_AVATAR_CONTENT_TYPE("Ảnh đại diện chỉ hỗ trợ JPG, PNG hoặc WEBP", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_FILE_EXTENSION("Tên file ảnh phải có đuôi JPG, PNG hoặc WEBP", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_OBJECT_KEY("Bạn không có quyền xác nhận ảnh này", HttpStatus.FORBIDDEN),
    AVATAR_NOT_UPLOADED("Ảnh chưa được upload thành công lên S3", HttpStatus.BAD_REQUEST),

    // Material / S3
    INVALID_MATERIAL_CONTENT_TYPE("Tài liệu chỉ hỗ trợ PDF, DOCX, PPTX, TXT hoặc ZIP", HttpStatus.BAD_REQUEST),
    INVALID_MATERIAL_FILE_EXTENSION("Tên file tài liệu phải có đuôi PDF, DOCX, PPTX, TXT hoặc ZIP", HttpStatus.BAD_REQUEST),
    INVALID_MATERIAL_OBJECT_KEY("Bạn không có quyền xác nhận tài liệu này", HttpStatus.FORBIDDEN),
    MATERIAL_NOT_UPLOADED("Tài liệu chưa được upload thành công lên S3", HttpStatus.BAD_REQUEST),
    MATERIAL_NOT_FOUND("Không tìm thấy tài liệu", HttpStatus.NOT_FOUND),
    MATERIAL_PROCESSING_JOB_NOT_FOUND("Không tìm thấy job xử lý tài liệu", HttpStatus.NOT_FOUND),
    MATERIAL_TEXT_EMPTY("Không đọc được nội dung tài liệu. File có thể là ảnh scan hoặc không chứa văn bản", HttpStatus.BAD_REQUEST),
    MATERIAL_PROCESSING_FAILED("Không thể xử lý tài liệu", HttpStatus.INTERNAL_SERVER_ERROR),

    // Cognito
    COGNITO_ATTRIBUTE_MISSING("Thiếu thông tin người dùng từ Cognito", HttpStatus.BAD_GATEWAY),
    COGNITO_SECRET_HASH_FAILED("Không thể tạo Cognito secret hash", HttpStatus.INTERNAL_SERVER_ERROR),
    COGNITO_SERVICE_ERROR("Không thể xử lý yêu cầu xác thực", HttpStatus.BAD_GATEWAY),

    // Common fallback
    FORBIDDEN("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus status;
}
