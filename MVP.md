# SkillSprint MVP Overview

## 1. Mục Tiêu Dự Án

SkillSprint là hệ thống học tập cá nhân hóa theo workspace. Người dùng tạo workspace cho một mục tiêu học tập, upload tài liệu, hệ thống xử lý nội dung, AI hoặc rule-based engine phân tích tài liệu và tạo roadmap học tập có thể thực hiện được.

Giá trị chính của sản phẩm là biến tài liệu thô thành lộ trình học rõ ràng:

```text
Tài liệu thô -> Cấu trúc học tập -> Roadmap -> Lịch học -> Tiến độ
```

MVP cần chứng minh core learning flow chạy được end-to-end, nhưng vẫn giữ bức tranh sản phẩm đủ đầy để schema và kiến trúc không bị cụt khi mở rộng.

## 2. Định Hướng Sản Phẩm

Sản phẩm đi theo mô hình `workspace-centered`.

Mỗi workspace đại diện cho một mục tiêu học tập riêng, ví dụ:

- Ôn thi một môn học.
- Học kỹ năng mới.
- Chuẩn bị chứng chỉ.
- Tự học theo tài liệu cá nhân.

Core flow đầy đủ:

```text
Login bằng Cognito
-> Sync user vào database
-> Tạo workspace
-> Nhập onboarding profile
-> Upload tài liệu
-> Lưu file gốc bằng S3 object key
-> Tạo material processing job
-> Extract text bằng Apache Tika
-> Clean/chunk tài liệu
-> AI/rule-based engine tạo learning structure
-> User review/confirm structure
-> Sinh chapters/topics
-> Sinh roadmap và roadmap steps
-> Gợi ý tài nguyên học
-> Sinh calendar tasks
-> User học và hoàn thành task
-> Hệ thống cập nhật progress
```

Core flow ưu tiên cho MVP đầu:

```text
Login -> Workspace -> Upload Material -> Review Structure -> Generate Roadmap
```

## 3. Core MVP Bắt Buộc

Đây là các phần cần làm để MVP có giá trị thật:

- AWS Cognito authentication.
- Sync current user vào database.
- RBAC cơ bản với 2 role: `LEARNER`, `ADMIN`.
- User profile và `/api/me`.
- Workspace CRUD.
- Onboarding profile cho workspace.
- Upload tài liệu và lưu metadata.
- Lưu file gốc bằng S3 bucket/object key.
- File extraction bằng Apache Tika.
- Material processing job để tracking pipeline.
- Extracted document và material chunks.
- AI hoặc rule-based learning structure generation.
- Review/confirm learning structure.
- Chapter/topic structure.
- Roadmap generation theo `roadmap_steps`.
- Resource suggestion cơ bản cho roadmap step.
- Calendar task generation cơ bản.
- Progress tracking cơ bản.

## 4. MVP-supported Skeleton

Các phần này có schema hoặc entity từ đầu để tránh thiết kế cụt, nhưng không triển khai logic đầy đủ trong vòng đầu:

- `reminders`: chuẩn bị cho nhắc nhở task/deadline/progress.
- `notifications`, `notification_logs`: chuẩn bị notification center và dispatch log.
- `business_activity_logs`: chuẩn bị audit/activity log nghiệp vụ.
- `study_sessions`, `pomodoro_sessions`: chuẩn bị tracking phiên học và Pomodoro.
- `service_plans`, `subscriptions`: chuẩn bị feature gating/quota/monetization sau này.

Nguyên tắc:

```text
Schema có thể chuẩn bị trước.
Service/API chỉ làm khi core flow đã ổn định.
```

## 5. Phase Later

Chưa triển khai sâu trong MVP đầu:

- Payment/subscription lifecycle đầy đủ.
- Quota enforcement nâng cao theo gói.
- Notification đa kênh: email, push, SMS.
- WebSocket realtime notification đầy đủ.
- Pomodoro nâng cao.
- Business activity analytics nâng cao.
- AI tutor nâng cao.
- Quiz generator nâng cao.
- RAG/ChromaDB sâu.
- Kafka/event streaming.
- ELK/observability production đầy đủ.

Chỉ chuyển sang các phần này sau khi flow user -> workspace -> material -> structure -> roadmap chạy ổn định.

## 6. Learning Structure Review Flow

AI không quyết định kết quả cuối cùng. AI chỉ đề xuất cấu trúc học tập, user review trước khi roadmap được tạo.

Flow:

```text
Material chunks
-> AI/rule-based structure generation
-> learning_structure_versions status = REVIEW_REQUIRED
-> User review structure tree
-> User chỉnh nội dung cơ bản nếu cần
-> User confirm
-> learning_structure_versions status = CONFIRMED
-> Roadmap generation dùng bản CONFIRMED
```

Trong MVP, user được sửa:

- Chapter title.
- Topic title.
- Summary.
- Thứ tự chapter/topic.
- Thêm topic.
- Xóa topic.
- Confirm structure.

Trong MVP, user chưa sửa:

- Metadata hệ thống như `status`, `generated_by`, `version_no`.
- `source_chunk_ids`.
- `is_ai_generated`.
- Difficulty/estimated minutes nếu chưa cần.
- JSONB sâu như `key_concepts`, `learning_outcomes`, `recommended_focus`.

Version rule cho MVP:

- Không tạo version mới cho mỗi lần user sửa nhỏ.
- User sửa draft trong bản `REVIEW_REQUIRED`.
- Khi confirm thì set bản đó thành `CONFIRMED`.
- `version_no` dùng cho regenerate hoặc major revision sau này.
- Roadmap chỉ sinh từ bản `CONFIRMED`.

## 7. Production-ready Baseline

Các nền tảng nên giữ từ đầu:

- PostgreSQL là database chính.
- Flyway là source of truth cho schema.
- Hibernate dùng `ddl-auto: validate`.
- AWS Cognito làm identity provider.
- `users.user_id` lưu Cognito `sub`.
- RBAC lưu trong database.
- AWS S3 private bucket/object key cho file gốc.
- Business-critical data lưu trong PostgreSQL.
- System log bằng SLF4J/Logback.
- API documentation bằng Swagger/OpenAPI.

Các phần production nâng cao nên để sau:

- Spring Scheduler phức tạp.
- WebSocket realtime đầy đủ.
- Spring AOP tracking cho request/service/business event.
- ELK/observability production đầy đủ.

## 8. Dữ Liệu Chính

Schema chính nằm ở:

```text
src/main/resources/db/migration/V1__init_schema.sql
```

Nhóm identity/RBAC:

- `users`: user nội bộ, dùng Cognito sub làm `user_id`.
- `roles`: role hệ thống, trước mắt gồm `LEARNER`, `ADMIN`.
- `permissions`: quyền cụ thể theo resource/action.
- `role_permissions`: role có những permissions nào.
- `user_roles`: user được gán role nào, có thể scoped theo workspace.

Nhóm workspace/onboarding:

- `study_workspaces`: workspace học tập.
- `onboarding_profiles`: mục tiêu học, deadline, lịch rảnh, confidence.

Nhóm material processing:

- `uploaded_materials`: metadata file upload và S3 object key.
- `extracted_documents`: nội dung đã extract/clean.
- `material_chunks`: các đoạn tài liệu đã chunk.
- `material_processing_jobs`: trạng thái pipeline xử lý tài liệu.

Nhóm learning structure:

- `learning_structure_versions`: phiên bản cấu trúc học tập do AI/user/system tạo.
- `chapters`: chapter trong structure version.
- `topics`: topic trong chapter.

Nhóm roadmap:

- `roadmaps`: roadmap tổng của workspace.
- `roadmap_steps`: từng bước học trong roadmap.
- `roadmap_step_resources`: tài nguyên học gợi ý cho step.
- `roadmap_progress_logs`: lịch sử thay đổi trạng thái roadmap step.

Nhóm calendar/progress:

- `calendar_tasks`: task/lịch học cụ thể.
- `calendar_schedule_runs`: lần sinh lịch học từ roadmap.
- `workspace_progress`: tiến độ tổng theo workspace.
- `progress_logs`: lịch sử tiến độ theo ngày.

Nhóm skeleton/phase later:

- `reminders`
- `notifications`, `notification_logs`
- `business_activity_logs`
- `study_sessions`, `pomodoro_sessions`
- `service_plans`, `subscriptions`

## 9. Trạng Thái Quan Trọng

Material upload:

```text
UPLOADED | FAILED
```

Material processing:

```text
PENDING -> EXTRACTING -> EXTRACTED -> CLEANING -> CHUNKING -> ANALYZING -> COMPLETED
```

Nếu cần user review:

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

Notification/reminder:

```text
PENDING -> SENT
```

Nếu lỗi hoặc hủy:

```text
FAILED | CANCELLED
```

## 10. Kiến Trúc Kỹ Thuật

Backend dùng Modular Monolith kết hợp Layered Architecture.

Stack chính:

- Backend: Spring Boot.
- Language: Java 17.
- Database: PostgreSQL.
- ORM: Spring Data JPA / Hibernate.
- Migration: Flyway.
- Authentication: AWS Cognito.
- Authorization: RBAC trong database.
- Object storage: AWS S3.
- Document extraction: Apache Tika.
- AI orchestration: LangChain4j hoặc provider tương đương.
- API docs: Swagger/OpenAPI.

Layering:

```text
controller -> service -> repository -> entity
dto/request, dto/response dùng ở API boundary
mapper dùng khi API shape khác entity shape
```

Entity không trả thẳng ra API. DTO chỉ tạo khi bắt đầu làm use case/API cụ thể.

## 11. Package Convention

Khi codebase lớn hơn, package nên chia theo domain nhẹ:

```text
entity.auth
entity.workspace
entity.material
entity.learningstructure
entity.roadmap
entity.calendar
entity.progress

repository.auth
repository.workspace
repository.material
repository.learningstructure
repository.roadmap
repository.calendar
repository.progress

controller.workspace
controller.material
controller.learningstructure
controller.roadmap
controller.calendar

service.auth
service.rbac
service.workspace
service.material
service.learningstructure
service.roadmap
service.calendar
```

Không tạo package rỗng. Chỉ tạo khi có class thật.

## 12. Thứ Tự Triển Khai Ưu Tiên

1. Foundation project.
2. Flyway schema.
3. Entity/repository baseline theo schema.
4. Validate entity baseline bằng `mvn test`.
5. Validate Flyway + Hibernate với PostgreSQL thật.
6. Cognito Resource Server.
7. Current user sync và `/api/me`.
8. RBAC baseline.
9. Workspace CRUD.
10. Onboarding profile.
11. Material upload metadata và S3 flow.
12. Material processing job.
13. Document extraction và chunking.
14. Learning structure generation.
15. Learning structure review/confirm.
16. Roadmap và roadmap steps.
17. Calendar task generation cơ bản.
18. Progress tracking cơ bản.

Sau đó mới mở rộng:

```text
reminder -> notification -> websocket -> pomodoro -> analytics -> payment/quota
```

## 13. Ưu Tiên Hiện Tại

Hiện tại nên tập trung:

1. Giữ `V1__init_schema.sql` làm source of truth.
2. Hoàn thành Phase 2 entity/repository baseline.
3. Chạy test với H2.
4. Chạy app với PostgreSQL thật để validate Flyway + Hibernate.
5. Setup Cognito Resource Server.
6. Làm `/api/me`.
7. Làm Workspace CRUD.

## 14. Kết Luận

MVP của SkillSprint nên giữ đầy đủ product vision nhưng implementation phải đi từng lát nhỏ, test được và có API contract rõ ràng.

Mục tiêu đầu tiên không phải làm hết mọi feature trong schema, mà là tạo được một vòng học tập chạy thật:

```text
Login -> Workspace -> Upload Material -> Review Structure -> Generate Roadmap
```
