# SkillSprint MVP

## 1. Mục Tiêu

SkillSprint là backend cho hệ thống học tập cá nhân hóa theo workspace.

Người dùng tạo một workspace cho mục tiêu học tập, upload tài liệu, hệ thống xử lý nội dung và tạo ra roadmap học tập có thể theo dõi bằng lịch học.

Giá trị chính:

```text
Tài liệu thô -> Cấu trúc học tập -> Roadmap -> Lịch học -> Tiến độ
```

MVP cần chứng minh một vòng học tập chạy thật từ đăng nhập đến tạo workspace, xử lý tài liệu, sinh roadmap và sinh lịch học cơ bản.

## 2. Core Flow

Flow sản phẩm đầy đủ:

```text
Login bằng Cognito
-> Sync user vào DB SkillSprint
-> Tạo workspace
-> Nhập onboarding profile
-> Upload tài liệu
-> Lưu metadata/file key
-> Tạo processing job
-> Extract text
-> Clean/chunk tài liệu
-> Sinh learning structure
-> User review/confirm structure
-> Sinh chapters/topics
-> Sinh roadmap/roadmap steps
-> Gợi ý tài nguyên học
-> Sinh calendar tasks từ roadmap
-> User học và complete task
-> Cập nhật progress
```

Flow ưu tiên hiện tại:

```text
Auth -> Workspace -> Onboarding -> Material -> Learning Structure -> Roadmap -> Calendar
```

## 3. Trạng Thái Hiện Tại

Đã có nền tảng:

- Spring Boot project baseline.
- PostgreSQL cho dev/prod, H2 cho test.
- Common API response bằng `ApiResponse`.
- Exception flow bằng `ErrorCode`, `AppException`, `GlobalExceptionHandler`.
- AWS Cognito authentication.
- Register, confirm register, resend confirmation code.
- Login Cognito.
- Complete new password cho user Cognito bị `NEW_PASSWORD_REQUIRED`.
- Spring Security Resource Server.
- Public `/health` và `/api/auth/**`.
- API nghiệp vụ còn lại cần JWT.
- Cognito groups map thành Spring roles.
- Sync user Cognito vào DB nội bộ.
- RBAC đơn giản bằng `roles` và `user_roles`.
- `RoleSeeder` tự tạo `ADMIN`, `LEARNER`.
- Flyway đã gỡ khỏi project.
- Permission-based access đã tạm bỏ khỏi MVP.
- `/api/me` lấy profile user hiện tại.
- `PATCH /api/me` cập nhật tên hiển thị.
- Avatar upload bằng S3 presigned URL.
- Admin user APIs: danh sách user, xem user, đổi status, đổi role.
- Workspace CRUD.
- Docker Postgres local dùng port ngoài `5434`.

Chưa làm:

- Onboarding API.
- Material upload/extraction/chunking.
- Roadmap API.
- Calendar generation API.
- Progress API.

## 4. Kiến Trúc Kỹ Thuật

Backend dùng Modular Monolith theo Layered Architecture.

Stack hiện tại:

- Java 17.
- Spring Boot 3.3.5.
- Maven.
- Spring Web.
- Spring Data JPA / Hibernate.
- Spring Validation.
- Spring Security.
- OAuth2 Resource Server.
- AWS Cognito User Pool.
- AWS SDK Cognito Identity Provider.
- PostgreSQL.
- H2 cho test.
- Lombok.

Schema strategy:

- Dev dùng Hibernate `ddl-auto: update`.
- Test dùng H2 `create-drop`.
- Project hiện tại không dùng Flyway.
- Production sau này nên chuyển sang migration có kiểm soát hoặc ít nhất `ddl-auto: validate`.

Layering:

```text
controller -> service -> repository -> entity
dto/request và dto/response nằm ở API boundary
common chứa ApiResponse và shared DTO nhỏ
exception chứa ErrorCode/AppException/GlobalExceptionHandler
configuration chứa Spring/Cognito/Security/seeder config
```

Quy tắc:

- Không trả entity trực tiếp ra API.
- DTO chỉ tạo khi có API/use case thật.
- Không tạo abstraction nếu chưa có nhu cầu thật.
- Ưu tiên role-based access trước, chưa dùng permission-based access.

## 5. Authentication Và Authorization

Cognito là nơi quản lý identity/authentication.

DB SkillSprint là nơi quản lý user profile nội bộ, role nội bộ và dữ liệu học tập.

Quy ước:

```text
users.user_id = Cognito sub
Cognito group ADMIN -> ROLE_ADMIN
Cognito group LEARNER -> ROLE_LEARNER
```

Auth endpoints:

```text
POST /api/auth/register
POST /api/auth/resend-confirmation-code
POST /api/auth/confirm-register
POST /api/auth/login
POST /api/auth/complete-new-password
POST /api/auth/forgot-password
POST /api/auth/confirm-forgot-password
POST /api/auth/logout
```

Register flow:

```text
Register
-> Cognito gửi code email
-> Confirm register
-> Add user vào group LEARNER
-> Sync user về DB
```

Login flow:

```text
Login
-> Cognito verify username/password
-> Backend đọc user + groups từ Cognito
-> Sync user về DB
-> Gán role nội bộ
-> Trả token
```

Manual Cognito user flow:

```text
Admin tạo user trên Cognito
-> User login bằng temporary password
-> Cognito trả NEW_PASSWORD_REQUIRED + session
-> complete-new-password
-> Cognito đổi password thành permanent
-> Backend sync user về DB
```

RBAC hiện tại:

- `ADMIN`.
- `LEARNER`.
- `roles`.
- `user_roles`.

Không dùng trong MVP hiện tại:

- `permissions`.
- `role_permissions`.

Current user endpoints:

```text
GET /api/me
PATCH /api/me
POST /api/me/avatar/upload-url
POST /api/me/avatar/confirm
```

Admin user endpoints:

```text
GET /api/admin/users
GET /api/admin/users/{userId}
PATCH /api/admin/users/{userId}/status
PATCH /api/admin/users/{userId}/roles
```

## 6. API Response Chuẩn

Success:

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {}
}
```

Created:

```json
{
  "success": true,
  "code": 201,
  "message": "Created successfully"
}
```

Error:

```json
{
  "success": false,
  "code": 409,
  "message": "Email này đã được đăng ký",
  "path": "/api/auth/register"
}
```

Validation error:

```json
{
  "success": false,
  "code": 400,
  "message": "Dữ liệu không hợp lệ",
  "path": "/api/auth/register",
  "errors": [
    {
      "field": "email",
      "message": "must not be blank"
    }
  ]
}
```

`code` là HTTP status dạng số:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
500 Internal Server Error
502 Bad Gateway
```

## 7. Dữ Liệu Chính

Nhóm identity/RBAC:

- `users`: user nội bộ, `user_id` là Cognito `sub`.
- `roles`: role hệ thống.
- `user_roles`: user được gán role nào, có thể scoped theo workspace sau này.

Nhóm workspace:

- `study_workspaces`: workspace học tập.
- `onboarding_profiles`: mục tiêu học, deadline, lịch rảnh, confidence.

Nhóm material:

- `uploaded_materials`: metadata tài liệu upload.
- `material_processing_jobs`: trạng thái pipeline xử lý tài liệu.
- `extracted_documents`: text đã extract/clean.
- `material_chunks`: chunks phục vụ AI/rule-based generation.

Avatar hiện tại đã dùng S3 riêng:

```text
POST /api/me/avatar/upload-url
-> Frontend PUT file lên S3 bằng presigned URL
-> POST /api/me/avatar/confirm
-> Backend kiểm tra object tồn tại trên S3
-> Lưu users.avatar_object_key
-> Response build avatarUrl từ public base URL + object key
```

Nhóm learning structure:

- `learning_structure_versions`: bản cấu trúc học tập.
- `chapters`: chapter trong structure.
- `topics`: topic trong chapter.

Nhóm roadmap:

- `roadmaps`: roadmap tổng của workspace.
- `roadmap_steps`: từng bước học.
- `roadmap_step_resources`: tài nguyên học gợi ý.
- `roadmap_progress_logs`: lịch sử thay đổi trạng thái step/roadmap.

Nhóm calendar/progress:

- `calendar_schedule_runs`: mỗi lần sinh lịch học từ roadmap.
- `calendar_tasks`: task học theo ngày/giờ.
- `workspace_progress`: tiến độ tổng của workspace.

Nhóm hỗ trợ có thể giữ nhưng chưa ưu tiên API:

- `reminders`.
- `notifications`.
- `study_sessions`.
- `pomodoro_sessions`.

Nhóm tạm chưa cần cho MVP hiện tại:

- `business_activity_logs`.
- `notification_logs`.
- `progress_logs`.
- `service_plans`.
- `subscriptions`.

## 8. Learning Structure Review

AI/rule-based engine chỉ đề xuất. User phải review trước khi roadmap được tạo.

Flow:

```text
Material chunks
-> Generate learning_structure_versions status REVIEW_REQUIRED
-> Generate chapters/topics
-> User review
-> User chỉnh phần được phép
-> User confirm
-> status CONFIRMED
-> Generate roadmap từ bản CONFIRMED
```

User được sửa trong MVP:

- Chapter title.
- Topic title.
- Summary.
- Thứ tự chapter/topic.
- Thêm topic.
- Xóa topic.
- Confirm structure.

User không được sửa trực tiếp:

- `structureVersionId`.
- `workspaceId`.
- `materialId`.
- `generatedBy`.
- `status`.
- `versionNo`.
- `sourceChunkIds`.
- `aiGenerated`.
- `createdAt`.
- `updatedAt`.
- `confirmedAt`.

Rule version:

- Không tạo version mới cho mỗi chỉnh sửa nhỏ.
- User sửa draft trong bản `REVIEW_REQUIRED`.
- Khi confirm thì set bản đó thành `CONFIRMED`.
- `version_no` dùng cho regenerate hoặc major revision sau.

## 9. Roadmap Và Calendar

Roadmap sinh từ learning structure đã confirm.

```text
Confirmed structure
-> Roadmap
-> Roadmap steps
-> Roadmap step resources
```

Calendar sinh từ roadmap:

```text
Roadmap steps
-> CalendarScheduleRun
-> CalendarTask theo ngày/giờ học
```

`CalendarScheduleRun` cần giữ vì nó lưu lịch sử mỗi lần sinh lịch, giúp:

- Preview lịch trước khi confirm.
- Regenerate lịch.
- Debug vì sao lịch được tạo như vậy.
- Theo dõi input như ngày rảnh, thời lượng học, số session mỗi ngày.

## 10. Trạng Thái Quan Trọng

User:

```text
ACTIVE | DISABLED
```

Learning structure:

```text
REVIEW_REQUIRED -> CONFIRMED
```

Roadmap:

```text
DRAFT -> ACTIVE -> COMPLETED
```

Roadmap step:

```text
LOCKED -> CURRENT -> COMPLETED
```

Calendar task:

```text
TODO -> COMPLETED
```

Material processing:

```text
PENDING -> EXTRACTING -> EXTRACTED -> CLEANING -> CHUNKING -> ANALYZING -> COMPLETED
FAILED
```

Reminder/notification:

```text
PENDING -> SENT
FAILED | CANCELLED
```

## 11. Thứ Tự Triển Khai

Đã làm:

1. Project baseline.
2. Common response/exception.
3. Cognito auth.
4. Security config.
5. User sync.
6. RoleSeeder.
7. Bỏ Flyway.
8. Bỏ permission-based tables khỏi MVP.
9. Current user APIs.
10. Admin user APIs.
11. S3 presigned avatar upload.
12. Docker Postgres local.
13. Workspace CRUD.

Làm tiếp:

1. Onboarding profile.
2. Material upload metadata theo workspace.
3. Material processing job.
4. Document extraction/chunking.
5. Learning structure generation.
6. Learning structure review/confirm.
7. Roadmap generation.
8. Calendar task generation.
9. Progress tracking.

Thứ tự API trước mắt:

```text
PUT /api/workspaces/{workspaceId}/onboarding
GET /api/workspaces/{workspaceId}/onboarding
```

## 12. Nguyên Tắc Ra Quyết Định

Giữ:

- Những bảng phục vụ trực tiếp core learning flow.
- Những abstraction đã có use case thật.
- Những config giúp người khác clone code chạy được.

Tạm bỏ hoặc chưa làm:

- Permission-based access khi chỉ có `ADMIN`/`LEARNER`.
- Payment/subscription khi chưa có quota/payment API.
- Business logs khi chưa có business event thật.
- Notification dispatch log khi chỉ cần in-app notification cơ bản.

Ưu tiên:

```text
Ít bảng nhưng dùng thật
Ít API nhưng chạy end-to-end
Code dễ hiểu trước, mở rộng sau
```

## 13. Kết Luận

MVP không cần làm hết mọi module ngay. Mục tiêu đầu tiên là tạo một vòng học tập thật sự chạy được:

```text
Login
-> Workspace
-> Upload Material
-> Generate/Review Structure
-> Generate Roadmap
-> Generate Calendar
-> Track Progress
```

Khi vòng này ổn, mới mở rộng sang reminder, notification realtime, Pomodoro, payment/quota và analytics.
