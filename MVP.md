# SkillSprint MVP Overview

## 1. Mục Tiêu Dự Án

SkillSprint là hệ thống học tập cá nhân hóa dựa trên workspace. Người dùng tạo workspace, upload tài liệu lên S3, hệ thống xử lý nội dung, AI phân tích tài liệu và tạo roadmap học tập có thể thực hiện được.

Giá trị chính của sản phẩm là biến tài liệu thô thành lộ trình học rõ ràng gồm nội dung cần học, bước học, lịch học, tiến độ, nhắc nhở và Pomodoro.

## 2. Định Hướng Sản Phẩm

Sản phẩm đi theo mô hình `workspace-centered`.

Core flow:

```text
User -> Workspace -> Material -> Extracted Document -> Material Chunks
-> Learning Structure -> Roadmap Steps -> Calendar Tasks -> Progress
```

Mỗi workspace đại diện cho một mục tiêu học tập riêng, ví dụ ôn thi một môn học, học kỹ năng mới, chuẩn bị chứng chỉ hoặc tự học theo tài liệu cá nhân.

## 3. Core MVP

Các chức năng bắt buộc trong MVP:

- AWS Cognito authentication.
- RBAC 2 role: `LEARNER`, `ADMIN`.
- User profile.
- Workspace management.
- Onboarding profile.
- Upload tài liệu lên AWS S3.
- File processing bằng Apache Tika.
- Material processing job để tracking trạng thái xử lý file.
- Extracted document và material chunks.
- AI/rule-based learning structure generation.
- Review/confirm learning structure.
- Chapter/topic generation.
- Roadmap generation theo `roadmap_steps`.
- Resource suggestion cho từng roadmap step.
- Calendar task scheduling.
- Eisenhower task classification.
- Progress tracking.
- Reminder cơ bản.
- In-app notification.
- Pomodoro session.
- Business activity log.

## 4. Production-ready Baseline

Các thành phần được thiết kế từ đầu để hệ thống sẵn sàng chạy thực tế:

- AWS S3 private bucket và object key cho file gốc.
- AWS Cognito làm identity provider; `users.user_id` là Cognito `sub`.
- Spring Security Resource Server để verify Cognito JWT.
- RBAC lưu trong database.
- Flyway quản lý schema PostgreSQL.
- Spring Scheduler cho overdue task, reminder và progress recalculation.
- WebSocket realtime notification.
- Business Log lưu trong PostgreSQL.
- System Log bằng SLF4J/Logback.
- Spring AOP tracking cho request/service/business event.
- ELK cho observability khi triển khai production.

## 5. Những Phần Để Phase Sau

Chưa làm trong MVP:

- Kafka.
- AI Tutor nâng cao.
- Quiz Generator nâng cao.
- RAG/ChromaDB sâu.
- Payment/subscription lifecycle đầy đủ.
- Notification đa kênh như email, push, SMS.
- Advanced analytics.

## 6. Luồng Người Dùng Chính

```text
Login bằng Cognito
-> Sync user vào database
-> Tạo workspace
-> Nhập onboarding profile
-> Upload tài liệu lên S3
-> Tạo material processing job
-> Extract text bằng Apache Tika
-> Clean/chunk tài liệu
-> AI phân tích learning structure
-> User review/confirm structure nếu cần
-> Sinh chapters/topics
-> Sinh roadmap và roadmap steps
-> Gợi ý tài nguyên học
-> Sinh calendar tasks
-> User học và hoàn thành task
-> Hệ thống cập nhật progress
-> Scheduler tạo reminder/notification khi cần
```

## 7. Dữ Liệu Chính

Schema chính hiện nằm ở:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

Các nhóm dữ liệu trong MVP:

- `users`: user nội bộ, dùng Cognito sub làm `user_id`.
- `roles`, `permissions`, `role_permissions`, `user_roles`: RBAC.
- `study_workspaces`: workspace học tập.
- `onboarding_profiles`: mục tiêu học, deadline, lịch rảnh, confidence.
- `uploaded_materials`: metadata file upload và S3 object key.
- `extracted_documents`: nội dung đã extract/clean.
- `material_chunks`: các đoạn tài liệu đã chunk để phục vụ AI parsing.
- `material_processing_jobs`: tracking pipeline xử lý tài liệu.
- `learning_structure_versions`: phiên bản cấu trúc học tập do AI/user/system tạo.
- `chapters`, `topics`: cấu trúc học tập đã sinh.
- `roadmaps`: roadmap tổng của workspace.
- `roadmap_steps`: từng bước học trong roadmap.
- `roadmap_step_resources`: tài nguyên học gợi ý cho step.
- `roadmap_progress_logs`: lịch sử thay đổi trạng thái roadmap step.
- `calendar_tasks`: task/lịch học cụ thể.
- `calendar_schedule_runs`: lần sinh lịch học từ roadmap.
- `workspace_progress`, `progress_logs`: tiến độ học tập.
- `reminders`: nhắc nhở task/deadline/progress.
- `notifications`, `notification_logs`: thông báo trong app và lịch sử gửi.
- `business_activity_logs`: audit/activity log nghiệp vụ.
- `study_sessions`, `pomodoro_sessions`: phiên học và Pomodoro.
- `service_plans`, `subscriptions`: skeleton cho feature gating và monetization sau này.

## 8. Trạng Thái Quan Trọng

Material upload:

```text
UPLOADED | FAILED
```

Material processing:

```text
PENDING -> EXTRACTING -> EXTRACTED -> CLEANING -> CHUNKING -> ANALYZING -> COMPLETED
```

Trường hợp cần người dùng kiểm tra:

```text
REVIEW_REQUIRED
```

Nếu lỗi:

```text
FAILED
```

Learning structure:

```text
REVIEW_REQUIRED -> CONFIRMED
```

Roadmap step:

```text
LOCKED -> CURRENT -> COMPLETED
```

Calendar task:

```text
TODO -> COMPLETED
```

Notification/reminder:

```text
PENDING -> SENT
```

Nếu lỗi hoặc hủy:

```text
FAILED | CANCELLED
```

## 9. Kiến Trúc Kỹ Thuật

Backend dùng Modular Monolith kết hợp Layered Architecture.

Công nghệ chính:

- Backend: Spring Boot.
- Database: PostgreSQL.
- ORM: Spring Data JPA / Hibernate.
- Migration: Flyway.
- Authentication: AWS Cognito.
- Authorization: RBAC trong database.
- Object storage: AWS S3.
- Document extraction: Apache Tika.
- AI orchestration: LangChain4j hoặc AI provider tương đương.
- Scheduler: Spring Scheduler.
- Realtime: WebSocket/STOMP.
- Logging: SLF4J, Logback, Spring AOP, ELK.
- API documentation: Swagger/OpenAPI.

## 10. Thứ Tự Triển Khai

1. Foundation project.
2. Database schema bằng Flyway.
3. Entity/repository theo schema hiện tại.
4. Cognito authentication.
5. RBAC baseline.
6. User profile và `/api/me`.
7. Workspace CRUD.
8. Onboarding profile.
9. S3 upload flow.
10. Material processing job.
11. Document extraction và chunking.
12. Learning structure generation.
13. Chapter/topic confirmation.
14. Roadmap và roadmap steps.
15. Calendar task scheduling.
16. Progress tracking.
17. Reminder và Pomodoro.
18. Notification và WebSocket.
19. Business log, AOP tracking và system logging.

## 11. Ưu Tiên Hiện Tại

Hiện tại nên tập trung theo thứ tự:

1. Giữ `V1__init_schema.sql` làm source of truth.
2. Tạo entity theo schema hiện tại.
3. Setup Cognito Resource Server.
4. Làm RBAC baseline.
5. Làm `/api/me`.
6. Làm workspace CRUD.

Sau khi user, RBAC và workspace ổn định, mới chuyển sang upload, S3, extraction, chunking, AI parsing và roadmap.

## 12. Kết Luận

MVP của SkillSprint tập trung vào core learning flow nhưng đã thiết kế sẵn production-ready foundation.

Mục tiêu không chỉ là lưu tài liệu, mà là tạo một hệ thống giúp người dùng học có kế hoạch, có lịch học cụ thể, có tiến độ rõ ràng và có reminder hỗ trợ duy trì việc học.
