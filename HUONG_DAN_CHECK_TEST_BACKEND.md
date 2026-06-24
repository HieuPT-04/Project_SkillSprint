# Huong dan check noi dung test backend SkillSprint

File nay dung de kiem tra lai cac viec da lam theo tai lieu Word "viec cua Bao trong ngay mai".

## 1. Chay toan bo test

Neu may da co Maven trong PATH:

```powershell
mvn test
```

Neu chua co Maven trong PATH, dung Maven kem IntelliJ:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6\plugins\maven\lib\maven3\bin\mvn.cmd' test
```

Lenh tren se chay unit test, repository test, API flow test va JaCoCo coverage check.

## 2. Kiem tra co test fail khong

Sau khi chay test, dung lenh nay de tim failure/error trong Surefire report:

```powershell
Select-String -Path 'target\surefire-reports\*.txt' -Pattern 'Failures: [1-9]|Errors: [1-9]'
```

Neu lenh khong in ra dong nao thi khong co test fail/error.

Co the xem nhanh cac report moi nhat:

```powershell
Get-ChildItem 'target\surefire-reports' | Sort-Object LastWriteTime -Descending | Select-Object -First 20 Name,LastWriteTime,Length
```

## 3. Kiem tra coverage

JaCoCo da duoc cau hinh trong `pom.xml` voi nguong:

- Instruction coverage >= 40%
- Branch coverage >= 20%

Neu coverage thap hon nguong, `mvn test` se fail.

Bao cao HTML nam o:

```text
target/site/jacoco/index.html
```

Bao cao CSV nam o:

```text
target/site/jacoco/jacoco.csv
```

Lenh xem nhanh ti le coverage hien tai:

```powershell
$rows = Import-Csv 'target\site\jacoco\jacoco.csv'
$im = ($rows | Measure-Object INSTRUCTION_MISSED -Sum).Sum
$ic = ($rows | Measure-Object INSTRUCTION_COVERED -Sum).Sum
$bm = ($rows | Measure-Object BRANCH_MISSED -Sum).Sum
$bc = ($rows | Measure-Object BRANCH_COVERED -Sum).Sum
[pscustomobject]@{
  InstructionRatio = [math]::Round($ic / ($im + $ic), 4)
  BranchRatio = [math]::Round($bc / ($bm + $bc), 4)
}
```

## 4. Cac nhom noi dung da duoc bo sung

### Service unit tests

Da them hoac bo sung test cho cac service quan trong:

- Onboarding profile: tao/cap nhat profile, giu ngon ngu cu, validate input, lay profile theo workspace.
- Roadmap: generate roadmap, versioning, validate learning structure, claim reward.
- Calendar: tao task, cap nhat task, conflict thoi gian, chan task cua user khac.
- Study session: start/reuse/finish session, Pomodoro, task completed, chan session cua user khac.
- Quiz: generate fallback quiz, che/gom dap an theo role, submit va cham diem.
- AI tutor: validate cau hoi, tao context, fallback khi AI response loi.
- Notification: tao/thong bao qua WebSocket, log thanh cong/that bai, mark as read.
- Point/leaderboard: cong diem duy nhat, bo qua duplicate, quiz upgrade bonus, diem duoi nguong pass.

### Repository/database tests

Da them `RepositoryQueryBehaviorTest` de check cac truy van database:

- Calendar overdue query.
- Point summary, rank va weekly leaderboard.
- Community post search/filter/hashtag/myPosts va loai bai da xoa.
- Payment search va revenue statistics.

### Security/RBAC va E2E flow

Repo da co san nhieu API flow test va tiep tuc duoc chay trong `mvn test`, bao gom:

- Auth flow.
- Admin user/dashboard/payment/subscription/service plan/leaderboard.
- Workspace/material/learning-roadmap-calendar.
- Study/quiz/tutor.
- Community room/core/chat.
- Progress/notification.
- Feedback/system announcement/maintenance.

Dung cac file trong `src/test/java/com/skillsprint/flow` de check lai cac luong nay.

### External adapter tests

Repo da co test cho cac adapter/chuc nang ngoai:

- Cognito secret hash.
- S3 presigned upload/view/confirm object key.
- Sepay payment service.
- Rate limit service.
- WebSocket access/dispatch o community va notification.
- AI draft/fallback logic cho quiz/tutor/learning document analyzer.

### Contract/regression

API flow tests dang dong vai tro regression cho response status, phan quyen, format loi va cac luong chinh. Khi them endpoint moi, nen them case vao nhom `flow` tuong ung de tranh thay doi response ngoai y muon.

## 5. Cach check tung nhom nhanh

Chay rieng service tests:

```powershell
mvn -Dtest='*ServiceTest' test
```

Chay rieng repository tests:

```powershell
mvn -Dtest='*RepositoryTest,RepositoryQueryBehaviorTest' test
```

Chay rieng API flow tests:

```powershell
mvn -Dtest='*FlowTest' test
```

Neu may chua co Maven trong PATH, thay `mvn` bang duong dan Maven IntelliJ o muc 1.

## 6. Viec can luu y khi review

- Neu chay bang JDK qua moi va thay log JaCoCo ve "Unsupported class file major version", hay doi sang JDK 17 dung voi `pom.xml` va GitHub Actions.
- CI hien dang chay `mvn -B test`, nen coverage gate se duoc check tu dong khi push len `main` hoac `master`.
- Test database hien dang chay H2 PostgreSQL mode. Neu can sat production hon nua, buoc tiep theo la them profile Testcontainers PostgreSQL rieng va copy cac repository test quan trong sang profile do.
