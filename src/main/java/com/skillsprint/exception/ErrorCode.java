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
    SESSION_EXPIRED("Phiên đăng nhập đã hết hạn hoặc đã bị thay thế", HttpStatus.UNAUTHORIZED),
    USER_CONTEXT_INVALID("Phiên đăng nhập không còn hợp lệ, vui lòng đăng nhập lại", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("Refresh token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
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
    CALENDAR_TASK_ALREADY_COMPLETED("Task đã hoàn thành không thể dời lịch", HttpStatus.CONFLICT),
    CALENDAR_TASK_TIME_CONFLICT("Khung giờ này đã có task khác", HttpStatus.CONFLICT),
    CALENDAR_TASK_STUDY_TIME_REQUIRED("Cần tích lũy đủ thời gian học hợp lệ trước khi hoàn thành task", HttpStatus.CONFLICT),
    CALENDAR_STUDY_DAYS_REQUIRED("Cần chọn ít nhất một ngày học trong tuần", HttpStatus.BAD_REQUEST),
    CALENDAR_AVAILABILITY_INSUFFICIENT("Số ngày học và khung giờ bạn chọn chưa đủ để xếp hết lộ trình trong thời hạn. Vui lòng chọn thêm ngày học, thêm khung giờ, tăng số giờ học mỗi tuần hoặc kéo dài thời hạn hoàn thành", HttpStatus.BAD_REQUEST),
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
    MATERIAL_WORKSPACE_LIMIT_EXCEEDED("Workspace này đã có tài liệu, vui lòng xóa tài liệu cũ trước khi tải tài liệu mới", HttpStatus.CONFLICT),
    MATERIAL_PROCESSING_JOB_NOT_FOUND("Không tìm thấy job xử lý tài liệu", HttpStatus.NOT_FOUND),
    MATERIAL_TEXT_EMPTY("Không đọc được nội dung tài liệu. File có thể là ảnh scan hoặc không chứa văn bản", HttpStatus.BAD_REQUEST),
    MATERIAL_PROCESSING_FAILED("Không thể xử lý tài liệu", HttpStatus.INTERNAL_SERVER_ERROR),

    // Study session
    STUDY_SESSION_NOT_FOUND("Không tìm thấy phiên học", HttpStatus.NOT_FOUND),
    STUDY_SESSION_TASK_ALREADY_COMPLETED("Task học này đã hoàn thành", HttpStatus.CONFLICT),
    POMODORO_SESSION_NOT_FOUND("Không tìm thấy Pomodoro session", HttpStatus.NOT_FOUND),
    POMODORO_SESSION_ALREADY_COMPLETED("Pomodoro session đã hoàn thành", HttpStatus.CONFLICT),
    POMODORO_SESSION_NOT_RUNNING("Pomodoro session không ở trạng thái đang chạy", HttpStatus.BAD_REQUEST),
    POMODORO_SESSION_NOT_PAUSED("Pomodoro session không ở trạng thái tạm dừng", HttpStatus.BAD_REQUEST),

    // Quiz
    QUIZ_NOT_FOUND("Không tìm thấy quiz", HttpStatus.NOT_FOUND),
    QUIZ_ATTEMPT_NOT_FOUND("Chưa có lượt làm quiz", HttpStatus.NOT_FOUND),
    QUIZ_INVALID_ANSWER("Đáp án quiz không hợp lệ", HttpStatus.BAD_REQUEST),
    QUIZ_GENERATION_FAILED("Không thể tạo quiz", HttpStatus.INTERNAL_SERVER_ERROR),
    QUIZ_GENERATION_UNAVAILABLE("Hiện chưa thể tạo quiz tự động. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),

    // Marketplace
    MARKETPLACE_ITEM_NOT_FOUND("Không tìm thấy Quiz Pack", HttpStatus.NOT_FOUND),
    MARKETPLACE_WORKSPACE_NOT_ELIGIBLE("Workspace cần có roadmap và đầy đủ quiz trước khi đăng bán", HttpStatus.BAD_REQUEST),
    MARKETPLACE_ITEM_NOT_EDITABLE("Quiz Pack hiện không thể chỉnh sửa", HttpStatus.CONFLICT),
    MARKETPLACE_CREATOR_VALIDATION_REQUIRED("Creator cần đạt ít nhất 90 điểm ở Full Pack Challenge trước khi gửi duyệt", HttpStatus.BAD_REQUEST),
    MARKETPLACE_ALREADY_PURCHASED("Bạn đã mua Quiz Pack này", HttpStatus.CONFLICT),
    MARKETPLACE_CREATOR_CANNOT_PURCHASE("Creator không thể mua Quiz Pack của chính mình", HttpStatus.BAD_REQUEST),
    MARKETPLACE_PACK_VERSION_NOT_FOUND("Không tìm thấy phiên bản Quiz Pack", HttpStatus.NOT_FOUND),
    MARKETPLACE_PACK_SALEABLE_VERSION_CONFLICT("Quiz Pack đã có một phiên bản đang được bán", HttpStatus.CONFLICT),
    WALLET_INSUFFICIENT_BALANCE("Số coin trong ví không đủ", HttpStatus.PAYMENT_REQUIRED),

    // AI Tutor
    TUTOR_QUESTION_REQUIRED("Câu hỏi không được để trống", HttpStatus.BAD_REQUEST),
    TUTOR_QUESTION_TOO_LONG("Câu hỏi quá dài", HttpStatus.BAD_REQUEST),
    TUTOR_CONTEXT_NOT_READY("Chưa có đủ nội dung học để AI Tutor trả lời", HttpStatus.BAD_REQUEST),
    TUTOR_SERVICE_ERROR("AI Tutor chưa thể trả lời lúc này", HttpStatus.BAD_GATEWAY),

    // Cognito
    COGNITO_ATTRIBUTE_MISSING("Thiếu thông tin người dùng từ Cognito", HttpStatus.BAD_GATEWAY),
    COGNITO_SECRET_HASH_FAILED("Không thể tạo Cognito secret hash", HttpStatus.INTERNAL_SERVER_ERROR),
    COGNITO_SERVICE_ERROR("Không thể xử lý yêu cầu xác thực", HttpStatus.BAD_GATEWAY),

    // Common fallback
    FORBIDDEN("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    VALIDATION_ERROR("Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED("Bạn thao tác quá nhanh, vui lòng thử lại sau", HttpStatus.TOO_MANY_REQUESTS),
    MAINTENANCE_MODE("Hệ thống đang bảo trì. Vui lòng quay lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),

    // Subscription / quota
    SERVICE_PLAN_NOT_FOUND("Không tìm thấy gói dịch vụ", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_NOT_FOUND("Không tìm thấy gói đang sử dụng", HttpStatus.NOT_FOUND),
    QUOTA_WORKSPACE_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số workspace của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_UPLOAD_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số tài liệu upload của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_FILE_SIZE_LIMIT_EXCEEDED("File vượt quá giới hạn dung lượng của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_STORAGE_LIMIT_EXCEEDED("Bạn đã đạt giới hạn dung lượng lưu trữ của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_AI_GENERATE_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số lần AI generate của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_COMMUNITY_ROOM_LIMIT_EXCEEDED("Bạn đã đạt giới hạn số phòng cộng đồng của gói hiện tại", HttpStatus.FORBIDDEN),
    QUOTA_ROADMAP_STEP_LOCKED("Vui lòng nâng cấp gói để học tiếp", HttpStatus.FORBIDDEN),
    PREMIUM_FEATURE_REQUIRED("Vui lòng nâng cấp gói để sử dụng tính năng này", HttpStatus.FORBIDDEN),

    // Payment
    PAYMENT_TRANSACTION_NOT_FOUND("Không tìm thấy giao dịch thanh toán", HttpStatus.NOT_FOUND),
    PAYMENT_INVALID_SIGNATURE("Chữ ký thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_AMOUNT("Số tiền thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_RECEIVER_ACCOUNT("Tài khoản nhận tiền không hợp lệ", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_CONFIRMED("Giao dịch đã được xác nhận trước đó", HttpStatus.CONFLICT),
    PAYMENT_PROVIDER_ERROR("Không thể xử lý thanh toán", HttpStatus.BAD_GATEWAY),
    PAYMENT_PLAN_NOT_PAYABLE("Gói này không cần thanh toán", HttpStatus.BAD_REQUEST),
    PAYMENT_PURPOSE_MISMATCH("Giao dịch thanh toán không đúng mục đích", HttpStatus.CONFLICT),
    COIN_TOP_UP_NOT_AVAILABLE("Nạp Coin hiện chưa khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    COIN_PACKAGE_NOT_FOUND("Không tìm thấy gói nạp Coin", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_DOWNGRADE_NOT_ALLOWED("Không thể mua gói thấp hơn gói hiện tại", HttpStatus.BAD_REQUEST),

    // Notification
    NOTIFICATION_NOT_FOUND("Không tìm thấy thông báo", HttpStatus.NOT_FOUND),

    // Feedback
    FEEDBACK_NOT_FOUND("Không tìm thấy feedback", HttpStatus.NOT_FOUND),

    // Feedback / S3
    INVALID_FEEDBACK_IMAGE_CONTENT_TYPE("Ảnh phản hồi chỉ hỗ trợ JPG, PNG, WEBP hoặc GIF", HttpStatus.BAD_REQUEST),
    INVALID_FEEDBACK_IMAGE_OBJECT_KEY("Bạn không có quyền đính kèm ảnh này", HttpStatus.FORBIDDEN),
    FEEDBACK_IMAGE_NOT_UPLOADED("Ảnh phản hồi chưa được upload thành công lên S3", HttpStatus.BAD_REQUEST),

    // Community
    COMMUNITY_POST_NOT_FOUND("Không tìm thấy bài viết cộng đồng", HttpStatus.NOT_FOUND),
    COMMUNITY_COMMENT_NOT_FOUND("Không tìm thấy bình luận", HttpStatus.NOT_FOUND),
    COMMUNITY_CONTENT_REQUIRED("Nội dung không được để trống", HttpStatus.BAD_REQUEST),
    COMMUNITY_CONTENT_TOO_LONG("Nội dung vượt quá giới hạn cho phép", HttpStatus.BAD_REQUEST),
    COMMUNITY_POST_NOT_VISIBLE("Bài viết chưa sẵn sàng để tương tác", HttpStatus.BAD_REQUEST),
    COMMUNITY_ACTION_NOT_ALLOWED("Bạn không có quyền thao tác với nội dung này", HttpStatus.FORBIDDEN),
    COMMUNITY_REPORT_DUPLICATED("Bạn đã báo cáo nội dung này trước đó", HttpStatus.CONFLICT),
    COMMUNITY_REPORT_NOT_FOUND("Không tìm thấy báo cáo cộng đồng", HttpStatus.NOT_FOUND),
    BLACKLIST_KEYWORD_NOT_FOUND("Không tìm thấy từ khóa blacklist", HttpStatus.NOT_FOUND),
    BLACKLIST_KEYWORD_DUPLICATED("Từ khóa blacklist đã tồn tại", HttpStatus.CONFLICT),
    COMMUNITY_ROOM_NOT_FOUND("Không tìm thấy phòng cộng đồng", HttpStatus.NOT_FOUND),
    COMMUNITY_ROOM_NAME_REQUIRED("Tên phòng không được để trống", HttpStatus.BAD_REQUEST),
    COMMUNITY_ROOM_NOT_ACTIVE("Phòng hiện không mở để tham gia", HttpStatus.BAD_REQUEST),
    COMMUNITY_ROOM_ALREADY_JOINED("Bạn đã tham gia phòng này", HttpStatus.CONFLICT),
    COMMUNITY_ROOM_MEMBER_NOT_FOUND("Không tìm thấy thành viên trong phòng", HttpStatus.NOT_FOUND),
    COMMUNITY_ROOM_MEMBER_BANNED("Bạn đã bị chặn khỏi phòng này", HttpStatus.FORBIDDEN),
    COMMUNITY_ROOM_OWNER_REQUIRED("Chỉ owner phòng mới được thực hiện thao tác này", HttpStatus.FORBIDDEN),
    COMMUNITY_ROOM_MODERATOR_REQUIRED("Chỉ owner hoặc moderator mới được thực hiện thao tác này", HttpStatus.FORBIDDEN),
    COMMUNITY_ROOM_OWNER_CANNOT_LEAVE("Owner cần chuyển quyền hoặc xóa phòng trước khi rời", HttpStatus.CONFLICT),
    COMMUNITY_ROOM_OWNER_ACTION_NOT_ALLOWED("Không thể thao tác quyền này với owner phòng", HttpStatus.CONFLICT),
    COMMUNITY_ROOM_INVITE_NOT_FOUND("Không tìm thấy lời mời vào phòng", HttpStatus.NOT_FOUND),
    COMMUNITY_ROOM_INVITE_DUPLICATED("Người dùng này đã có lời mời đang chờ", HttpStatus.CONFLICT),
    COMMUNITY_ROOM_INVITE_NOT_PENDING("Lời mời này không còn hiệu lực", HttpStatus.CONFLICT),
    COMMUNITY_CHAT_MESSAGE_NOT_FOUND("Không tìm thấy tin nhắn", HttpStatus.NOT_FOUND),
    COMMUNITY_CHAT_MESSAGE_REQUIRED("Tin nhắn không được để trống", HttpStatus.BAD_REQUEST),
    COMMUNITY_CHAT_MESSAGE_TOO_LONG("Tin nhắn tối đa 2000 ký tự", HttpStatus.BAD_REQUEST),
    COMMUNITY_CHAT_MEMBER_MUTED("Bạn đang bị mute trong phòng này", HttpStatus.FORBIDDEN),
    COMMUNITY_CHAT_AUTH_REQUIRED("Cần xác thực WebSocket trước khi gửi tin nhắn", HttpStatus.UNAUTHORIZED),
    COMMUNITY_PIN_NOT_FOUND("Không tìm thấy nội dung ghim", HttpStatus.NOT_FOUND),
    COMMUNITY_PIN_TITLE_REQUIRED("Tiêu đề ghim không được để trống", HttpStatus.BAD_REQUEST),
    COMMUNITY_PIN_CONTENT_REQUIRED("Nội dung ghim không được để trống", HttpStatus.BAD_REQUEST),
    COMMUNITY_PIN_MESSAGE_REQUIRED("Cần chọn tin nhắn để ghim", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus status;
}
