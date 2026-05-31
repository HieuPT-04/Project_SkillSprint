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

    // Learning structure
    LEARNING_STRUCTURE_NOT_FOUND("Không tìm thấy learning structure", HttpStatus.NOT_FOUND),
    LEARNING_STRUCTURE_ALREADY_CONFIRMED("Learning structure đã được xác nhận", HttpStatus.BAD_REQUEST),
    LEARNING_STRUCTURE_NOT_CONFIRMED("Learning structure chưa được xác nhận", HttpStatus.BAD_REQUEST),
    MATERIAL_CHUNKS_NOT_READY("Chưa có material chunks đã xử lý để tạo learning structure", HttpStatus.BAD_REQUEST),
    LEARNING_STRUCTURE_GENERATION_FAILED("Không thể tạo learning structure", HttpStatus.INTERNAL_SERVER_ERROR),

    // Roadmap
    ROADMAP_NOT_FOUND("Không tìm thấy roadmap", HttpStatus.NOT_FOUND),
    ROADMAP_CONFIRMED_STRUCTURE_REQUIRED("Cần xác nhận learning structure trước khi tạo roadmap", HttpStatus.BAD_REQUEST),
    ROADMAP_TOPICS_NOT_READY("Learning structure chưa có topic để tạo roadmap", HttpStatus.BAD_REQUEST),
    ROADMAP_GENERATION_FAILED("Không thể tạo roadmap", HttpStatus.INTERNAL_SERVER_ERROR),

    // Calendar
    CALENDAR_ROADMAP_REQUIRED("Cần tạo roadmap trước khi sinh lịch học", HttpStatus.BAD_REQUEST),
    CALENDAR_ALREADY_GENERATED("Lịch học đã được tạo, vui lòng dời từng buổi học thay vì tạo lại", HttpStatus.CONFLICT),
    CALENDAR_TASK_NOT_FOUND("Không tìm thấy calendar task", HttpStatus.NOT_FOUND),
    CALENDAR_INVALID_TIME_RANGE("Giờ kết thúc phải sau giờ bắt đầu", HttpStatus.BAD_REQUEST),
    CALENDAR_STUDY_DAYS_REQUIRED("Cần chọn ít nhất một ngày học trong tuần", HttpStatus.BAD_REQUEST),
    CALENDAR_TIME_SLOT_REQUIRED("Cần chọn khung giờ học trước khi sinh lịch học", HttpStatus.BAD_REQUEST),
    CALENDAR_ONBOARDING_REQUIRED("Cần setup onboarding trước khi sinh lịch học", HttpStatus.BAD_REQUEST),

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

    // Study session
    STUDY_SESSION_NOT_FOUND("Không tìm thấy phiên học", HttpStatus.NOT_FOUND),
    STUDY_SESSION_TASK_ALREADY_COMPLETED("Task học này đã hoàn thành", HttpStatus.CONFLICT),

    // Cognito
    COGNITO_ATTRIBUTE_MISSING("Thiếu thông tin người dùng từ Cognito", HttpStatus.BAD_GATEWAY),
    COGNITO_SECRET_HASH_FAILED("Không thể tạo Cognito secret hash", HttpStatus.INTERNAL_SERVER_ERROR),
    COGNITO_SERVICE_ERROR("Không thể xử lý yêu cầu xác thực", HttpStatus.BAD_GATEWAY),

    // Common fallback
    FORBIDDEN("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),

    // Subscription / quota
    SERVICE_PLAN_NOT_FOUND("Không tìm thấy gói dịch vụ", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_NOT_FOUND("Không tìm thấy gói đang sử dụng", HttpStatus.NOT_FOUND),
    QUOTA_WORKSPACE_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số workspace của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_UPLOAD_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số tài liệu upload của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_FILE_SIZE_LIMIT_EXCEEDED("File vượt quá giới hạn dung lượng của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_STORAGE_LIMIT_EXCEEDED("Bạn đã đạt giới hạn dung lượng lưu trữ của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_AI_GENERATE_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số lần AI generate của gói hiện tại", HttpStatus.FORBIDDEN),

    // Payment
    PAYMENT_TRANSACTION_NOT_FOUND("Không tìm thấy giao dịch thanh toán", HttpStatus.NOT_FOUND),
    PAYMENT_INVALID_SIGNATURE("Chữ ký thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_AMOUNT("Số tiền thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_RECEIVER_ACCOUNT("Tài khoản nhận tiền không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_CONFIRMED("Giao dịch đã được xác nhận trước đó", HttpStatus.CONFLICT),
    PAYMENT_PROVIDER_ERROR("Không thể xử lý thanh toán", HttpStatus.BAD_GATEWAY),
    PAYMENT_PLAN_NOT_PAYABLE("Gói này không cần thanh toán", HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED("Không thể mua gói thấp hơn gói hiện tại", HttpStatus.BAD_REQUEST),

    // Notification
    NOTIFICATION_NOT_FOUND("Không tìm thấy thông báo", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
