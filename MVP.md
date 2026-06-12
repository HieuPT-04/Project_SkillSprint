# SkillSprint MVP

## 1. Mục Tiêu

SkillSprint là backend cho hệ thống học tập cá nhân hóa theo workspace.

Người dùng tạo một workspace cho mục tiêu học tập, upload tài liệu, hệ thống xử lý nội dung và tạo ra roadmap học tập có thể theo dõi bằng lịch học.

Giá trị chính:

```text
Tài liệu thô -> Cấu trúc học tập -> Roadmap -> Lịch học -> Phiên học -> Quiz/AI Tutor -> Tiến độ
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
-> User mở study session từ calendar task
-> User học, hỏi AI Tutor hoặc làm quiz nếu có Premium
-> User complete task
-> Cập nhật progress
```

Flow ưu tiên hiện tại:

```text
Auth -> Workspace -> Onboarding -> Material -> Learning Structure -> Roadmap -> Calendar -> Study Session -> Quiz/AI Tutor -> Progress
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
- Onboarding API.
- Docker Postgres local dùng port ngoài `5434`.
- Material presigned upload URL, confirm upload, metadata list.
- Material processing job runner, text extraction, chunking.
- Learning structure generation bằng Gemini AI, fallback rule-based nếu AI chưa sẵn sàng.
- Roadmap generation API.
- Roadmap resources cho document section, video search query và practice prompt.
- Calendar generation API từ roadmap/onboarding.
- Calendar AI planner dùng Gemini khi có key, fallback rule-based nếu AI lỗi hoặc dữ liệu không hợp lệ.
- Calendar task update/complete API.
- Calendar task response có `overdue` và `studySessionEndpoint` để FE mở màn học ngay.
- Study session detail API để user bấm calendar task là vào màn học.
- Study session API để start/finish phiên học thật từ calendar task.
- Progress dashboard API gồm roadmap progress, current step, today/overdue tasks, study stats và current session.
- Quiz API cho từng roadmap step, tạo 5 câu, lưu đáp án đúng và chấm điểm khi user submit.
- AI Tutor API dạng chatbox theo workspace hoặc roadmap step.
- Quiz và AI Tutor chỉ mở cho gói `PREMIUM`; gói thấp hơn nhận lỗi `403`.
- Redis-backed session tracking để hỗ trợ logout và kiểm soát phiên đăng nhập.
- Refresh token API để user học lâu không bị out khi access token hết hạn.
- Eisenhower daily board API để FE gom task theo 4 nhóm trong ngày.
- Pomodoro/study timer basic để user start/finish phiên học.
- Subscription/quota basic.
- SePay payment flow cho thanh toán thật bằng chuyển khoản ngân hàng.
- Admin payment APIs cho danh sách giao dịch và reconcile thủ công khi cần.
- Admin Dashboard API gồm tổng quan user, workspace, subscription, payment, learning, chart và alert.
- Notification/reminder basic APIs.
- Feedback APIs cho user gửi góp ý/báo lỗi và admin quản lý feedback.

Cần rà soát tiếp:

- Test lại full core flow end-to-end bằng Postman.
- Cập nhật Postman collection mỗi khi API/response đổi.
- Chỉ sửa core nếu test phát hiện lỗi làm gãy flow.
- Chưa ưu tiên realtime notification, AI Tutor chat history và observability nâng cao.

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
POST /api/auth/refresh-token
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
-> Tạo Redis session
-> Trả token + sessionId
```

Refresh token flow:

```text
Access token gần hết hạn
-> FE gọi refresh-token bằng refreshToken + X-Session-Id
-> Backend kiểm tra Redis session còn hợp lệ
-> Backend xin access token mới từ Cognito
-> Backend gia hạn Redis session
-> Trả access token mới + sessionId cũ
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

Admin dashboard/payment endpoints:

```text
GET /api/admin/dashboard
GET /api/admin/dashboard?from=yyyy-MM-dd&to=yyyy-MM-dd
GET /api/admin/payments
POST /api/admin/payments/{paymentId}/reconcile
PATCH /api/admin/users/{userId}/subscription
```

Notification/reminder endpoints:

```text
GET /api/notifications
GET /api/notifications/unread
PATCH /api/notifications/{notificationId}/read
POST /api/workspaces/{workspaceId}/reminders
```

Feedback endpoints:

```text
POST /api/feedback
GET /api/admin/feedback
GET /api/admin/feedback/{feedbackId}
PATCH /api/admin/feedback/{feedbackId}/status
DELETE /api/admin/feedback/{feedbackId}
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
- `study_sessions`: phiên học thật của user từ calendar task.
- `pomodoro_sessions`: phiên Pomodoro/timer học tập của user.
- `workspace_progress`: tiến độ tổng của workspace.
- `progress_logs`: lịch sử thay đổi progress nếu cần audit.

Nhóm quiz/AI tutor:

- `quizzes`: quiz gắn với user, workspace và roadmap step.
- `quiz_questions`: câu hỏi quiz, hiện ưu tiên multiple-choice.
- `quiz_options`: đáp án lựa chọn, có lưu đáp án đúng để backend tự chấm.
- `quiz_attempts`: mỗi lần user nộp quiz.
- `quiz_attempt_answers`: chi tiết đáp án user đã chọn.
- AI Tutor hiện chưa lưu lịch sử chat trong MVP; response được tạo theo context workspace/roadmap step/material chunks.

Nhóm payment/subscription:

- `service_plans`: gói dịch vụ cố định.
- `subscriptions`: gói hiện tại của user.
- `payment_transactions`: giao dịch thanh toán SePay.

Nhóm notification/reminder:

- `reminders`.
- `notifications`.
- `notification_logs`.

Nhóm feedback:

- `feedbacks`: feedback/bug report của user để admin theo dõi và xử lý.

Nhóm hỗ trợ có thể giữ nhưng chưa ưu tiên API:

- `business_activity_logs`.

## 8. Learning Structure Review

AI/rule-based engine chỉ đề xuất. User phải review trước khi roadmap được tạo.

Hiện tại backend ưu tiên Gemini AI khi có `GEMINI_API_KEY`. Nếu thiếu key, AI lỗi, response rỗng hoặc JSON không đạt validation tối thiểu, backend tự fallback về rule-based generator để flow vẫn chạy.

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

Nếu Gemini đã cấu hình, backend gửi draft calendar sang AI để sắp xếp ngày/giờ hợp lý hơn theo deadline, difficulty, thời lượng học và ngày rảnh. Nếu AI không sẵn sàng hoặc trả dữ liệu sai, backend dùng rule-based calendar để không làm gãy flow.

Study session mở màn học từ calendar:

```text
CalendarTask
-> Study Session Detail
-> Roadmap step summary/key concepts/learning outcomes
-> Practice prompt
-> Resources
-> Start/finish study session
-> Complete calendar task
-> Update progress
```

Quiz quick check:

```text
Roadmap step
-> Generate quiz
-> Gemini tạo 5 câu nếu sẵn sàng, fallback rule-based nếu AI lỗi
-> Backend lưu câu hỏi, options và đáp án đúng
-> User submit đáp án
-> Backend chấm điểm, trả score/pass/results
```

AI Tutor:

```text
User hỏi từ chatbox global hoặc từ roadmap step
-> Backend kiểm tra user có gói PREMIUM
-> Backend gom context từ workspace/roadmap/calendar/material chunks
-> Gemini trả answer + suggestedQuestions + confidence
-> Backend làm gọn response, fallback nếu AI lỗi
```

Quota hiện tại:

```text
FREE / SKILL_BUILDER: không dùng Quiz và AI Tutor
PREMIUM: được dùng Quiz và AI Tutor
```

Eisenhower daily board:

```text
GET /api/workspaces/{workspaceId}/calendar/eisenhower?date=yyyy-MM-dd
```

Mục tiêu:

- FE lấy task trong một ngày và render 4 cột: làm ngay, lên lịch, để sau, loại bỏ.
- Backend phân loại dựa trên `isUrgent`, `isImportant`, `priority`, `taskDate` và metadata đã có trên calendar task.
- Không tạo bảng mới, chỉ đọc và nhóm lại từ `calendar_tasks`.

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
UPCOMING -> CURRENT -> COMPLETED
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
14. Onboarding profile.
15. Material presigned upload URL, confirm upload, metadata list.
16. Material processing job runner, text extraction, chunking.
17. Rule-based learning structure generation, get latest, confirm.
18. Gemini AI learning structure generation với rule-based fallback.
19. Roadmap generation từ confirmed learning structure.
20. Roadmap step resources cho tài liệu, video search và bài luyện tập.
21. Calendar task generation từ roadmap và onboarding setup.
22. Calendar task update/complete.
23. Study session start/finish từ calendar task.
24. Progress dashboard cho workspace.
25. Study session detail API để mở màn học từ calendar task.
26. Calendar AI planner với rule-based fallback.
27. Roadmap/Calendar response rút gọn cho FE, có `overdue` và `studySessionEndpoint`.
28. Progress dashboard có study stats, streak và current session.
29. Subscription/quota basic.
30. SePay payment flow với pending, expireAt, webhook và activate subscription 1 tháng.
31. Subscription trả phí hết hạn theo `startAt/endAt` và tự fallback về FREE.
32. Admin payment list/reconcile và admin subscription adjustment.
33. Redis-backed session tracking cho logout/session control.
34. Refresh token API và Redis session TTL theo session policy.
35. Pomodoro/study timer basic.
36. Eisenhower daily board API.
37. Admin Dashboard API với overview, chart, alert và recent activity.
38. Quiz API theo roadmap step, tạo 5 câu, submit và xem attempt mới nhất.
39. AI Tutor API theo workspace/roadmap step, response gọn cho FE.
40. Giới hạn Quiz và AI Tutor chỉ cho gói `PREMIUM`.
41. Notification/reminder basic APIs.
42. Feedback user/admin APIs.

Làm tiếp:

1. Rà soát full core flow end-to-end.
2. Chuẩn hóa thêm API contract nếu FE cần field cụ thể.
3. Sửa lỗi core nếu phát hiện trong lúc test.
4. Sau khi core ổn mới chọn Phase Later đầu tiên: chat history AI Tutor, notification hoặc observability.

Thứ tự kiểm thử trước mắt:

```text
Login
-> Create workspace
-> Upsert onboarding
-> Upload material bằng presigned URL
-> Confirm material upload
-> Wait material processing completed
-> Generate learning structure
-> Confirm learning structure
-> Generate roadmap
-> Generate calendar
-> Get study session detail từ calendar task
-> Start/finish study session
-> Generate/submit quiz nếu user là PREMIUM
-> Ask AI Tutor nếu user là PREMIUM
-> Check progress dashboard
```

## 12. Nguyên Tắc Ra Quyết Định

Giữ:

- Những bảng phục vụ trực tiếp core learning flow.
- Những abstraction đã có use case thật.
- Những config giúp người khác clone code chạy được.

Tạm bỏ hoặc chưa làm:

- Permission-based access khi chỉ có `ADMIN`/`LEARNER`.
- Payment/subscription nâng cao nếu basic flow chưa ổn.
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
-> Open Study Session
-> Quiz/AI Tutor cho Premium
-> Track Progress
```

Khi vòng này ổn, mới mở rộng sang realtime notification, AI Tutor chat history, audit/business log, observability và admin analytics nâng cao.
