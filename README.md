# SkillSprint Backend

Spring Boot backend cho SkillSprint, hệ thống học tập cá nhân hóa theo workspace.

Core MVP flow:

```text
Auth -> User Profile -> Workspace -> Onboarding -> Material -> Learning Structure -> Roadmap -> Calendar -> Progress
```

## Stack

- Java 17+
- Spring Boot 3.3.5
- Maven
- PostgreSQL cho dev/prod
- H2 cho test
- Hibernate `ddl-auto: update` cho dev
- AWS Cognito cho authentication
- Cognito groups + DB roles cho authorization
- AWS S3 presigned URL cho upload avatar/material sau này

## Local Setup

Tạo file `.env` ở root project:

```env
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev

POSTGRES_PORT=5434
DB_URL=jdbc:postgresql://localhost:5434/skillsprint
DB_USERNAME=postgres
DB_PASSWORD=root

COGNITO_REGION=ap-southeast-1
COGNITO_USER_POOL_ID=your-user-pool-id
COGNITO_CLIENT_ID=your-client-id
COGNITO_CLIENT_SECRET=your-client-secret
COGNITO_DEFAULT_GROUP=LEARNER
COGNITO_ISSUER_URI=https://cognito-idp.ap-southeast-1.amazonaws.com/your-user-pool-id

AWS_REGION=ap-southeast-1
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_S3_BUCKET=your-bucket-name
AWS_S3_PUBLIC_BASE_URL=https://your-bucket-name.s3.ap-southeast-1.amazonaws.com
AWS_S3_UPLOAD_URL_EXPIRATION_MINUTES=10
```

Không commit `.env`.

## Run PostgreSQL

```bash
docker compose up -d postgres
```

Docker mapping:

```text
localhost:5434 -> container:5432
database: skillsprint
username: postgres
password: root
```

Reset database sạch:

```bash
docker compose down -v
docker compose up -d postgres
```

Sau đó chạy lại app, Hibernate sẽ tự sinh bảng từ entity.

## Run App

```bash
mvn spring-boot:run
```

Hoặc chạy `com.skillsprint.SkillSprintApplication` trong IntelliJ.

IntelliJ nên set:

```text
Working directory: /Users/tanhieu2004/Documents/GitHub/Project_SkillSprint
VM options: --enable-native-access=ALL-UNNAMED
```

Health check:

```bash
curl http://localhost:8080/health
```

## Test

```bash
mvn test
```

## Auth Endpoints

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

## Current User Endpoints

```text
GET /api/me
PATCH /api/me
POST /api/me/avatar/upload-url
POST /api/me/avatar/confirm
```

Avatar upload flow:

```text
1. Frontend gọi POST /api/me/avatar/upload-url
2. Backend trả uploadUrl, objectKey, fileUrl
3. Frontend PUT binary image lên uploadUrl
4. Frontend gọi POST /api/me/avatar/confirm với objectKey
5. Backend kiểm tra object trên S3 và lưu users.avatar_object_key
6. GET /api/me build avatarUrl từ AWS_S3_PUBLIC_BASE_URL + avatar_object_key
```

Lưu ý: database chỉ lưu object key, không lưu full URL. Bucket đang nên để private. `avatarUrl` trong response là URL được backend build từ S3/CloudFront base URL.

## Admin User Endpoints

Yêu cầu token có role `ADMIN`.

```text
GET /api/admin/users
GET /api/admin/users/{userId}
PATCH /api/admin/users/{userId}/status
PATCH /api/admin/users/{userId}/roles
```

## Workspace Endpoints

Yêu cầu token hợp lệ. User chỉ thao tác được workspace của chính mình.

```text
POST /api/workspaces
GET /api/workspaces
GET /api/workspaces/{workspaceId}
PATCH /api/workspaces/{workspaceId}
DELETE /api/workspaces/{workspaceId}
```

Delete workspace là soft delete bằng status `DELETED`.

## Onboarding Endpoints

Yêu cầu token hợp lệ. Onboarding luôn thuộc một workspace của current user.

```text
PUT /api/workspaces/{workspaceId}/onboarding
GET /api/workspaces/{workspaceId}/onboarding
```

`PUT` là upsert: chưa có thì tạo mới, có rồi thì cập nhật.

## Material Endpoints

Yêu cầu token hợp lệ. User chỉ thao tác được material trong workspace của chính mình.

```text
POST /api/workspaces/{workspaceId}/materials/upload-url
POST /api/workspaces/{workspaceId}/materials/confirm
GET /api/workspaces/{workspaceId}/materials
GET /api/workspaces/{workspaceId}/materials/{materialId}/processing-job
```

Material upload flow:

```text
1. Frontend gọi POST /api/workspaces/{workspaceId}/materials/upload-url
2. Backend trả uploadUrl, objectKey, fileUrl
3. Frontend PUT binary file lên uploadUrl
4. Frontend gọi POST /api/workspaces/{workspaceId}/materials/confirm
5. Backend kiểm tra object tồn tại trên S3
6. Backend lưu uploaded_materials và tạo material_processing_jobs status PENDING
7. Background runner đọc file từ S3, extract text, tách chunk và lưu material_chunks
```

Nếu PDF là ảnh scan hoặc file không có text, job sẽ chuyển sang `FAILED` và trả `errorMessage` rõ ràng để FE hiển thị.

## Learning Structure Endpoints

Yêu cầu token hợp lệ. User chỉ tạo/xem/confirm learning structure trong workspace của chính mình.

```text
POST /api/workspaces/{workspaceId}/learning-structure/generate
GET /api/workspaces/{workspaceId}/learning-structure
POST /api/workspaces/{workspaceId}/learning-structure/confirm
```

MVP hiện tại tạo learning structure bằng rule-based generator từ `material_chunks`, chưa gọi AI thật.

## API Response

Success:

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {}
}
```

Error:

```json
{
  "success": false,
  "code": 403,
  "message": "Bạn không có quyền thực hiện thao tác này",
  "path": "/api/admin/users"
}
```

## Current Next Tasks

Theo MVP, phần tiếp theo nên làm:

```text
Learning Structure
-> Roadmap
-> Calendar
-> Progress
```
