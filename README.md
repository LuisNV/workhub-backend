# Workhub Backend

Java 21 · Spring Boot 3.5.14 · Maven · PostgreSQL 17 · Flyway · Spring Modulith (kiểm tra ranh giới module).
Ứng dụng chia theo module nghiệp vụ: `user` (hồ sơ, avatar), `auth` (đăng nhập, refresh token),
và `platform` (hạ tầng dùng chung).

## 1. Chuẩn bị
- JDK 21 (`java -version`). Đặt Project SDK/Maven runner trong IntelliJ là 21.
- Docker Desktop đang chạy để chạy PostgreSQL và test tích hợp.
- Internet lần đầu để Maven tải dependencies và Docker tải image.
- Mở thư mục này hoặc pom.xml trong IntelliJ/VS Code.

## 2. Chạy trên macOS
Tại thư mục có pom.xml:
```bash
chmod +x mvnw
docker compose up -d --wait
./mvnw spring-boot:run
```
`spring-boot:run` mặc định bật profile `local` (cấu hình trong `pom.xml`). Kiểm tra log khởi động có dòng
`The following 1 profile is active: "local"`. Muốn đổi profile: `./mvnw spring-boot:run "-Dspring-boot.run.profiles=ten"`
(PowerShell bắt buộc có dấu nháy).
Windows: dùng `mvnw.cmd` thay `./mvnw`.
Nếu đã cài Maven có thể dùng `mvn` thay wrapper.

API ở http://localhost:8081. Database ở localhost:5433, database/user `workhub`,
password `workhub_local` (chỉ dùng học trên máy cá nhân).

Tài khoản demo (chỉ có ở profile `local`): số điện thoại `090 123 4567`, mật khẩu `Admin@123456`.

```bash
curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"090 123 4567","password":"Admin@123456"}'
# Lấy data.accessToken rồi:
curl -i http://localhost:8081/api/v1/auth/me -H "Authorization: Bearer <accessToken>"
curl -i http://localhost:8081/actuator/health
```

Mọi response dưới `/api/**` đều có dạng:
```json
{"code":0,"httpStatus":200,"message":"Thành công","data":{...},"traceId":"..."}
```
Xem bảng mã lỗi ở mục 4.

## 3. Cấu trúc module

```
com.thanh.workhub
├─ WorkhubApplication
├─ platform/                    hạ tầng dùng chung (module OPEN), KHÔNG import module nghiệp vụ
│  ├─ web/        ApiResponse, ApiResponseAdvice, ErrorCode, ApiException,
│  │              GlobalExceptionHandler, TraceIdFilter
│  ├─ security/   SecurityConfig, JwtService, @CurrentUserId
│  └─ storage/    AvatarStorage, CloudinaryConfig
├─ user/
│  ├─ UserApi, UserCredentials, UserProfileResponse   ← public API của module
│  └─ internal/   api (UserController, dto), application (UserService, AvatarService),
│                 domain (User), persistence (UserRepository)
└─ auth/
   └─ internal/   api (AuthController, dto), application (AuthService, RefreshTokenService),
                  domain (RefreshToken), persistence (RefreshTokenRepository)
```

Quy tắc ranh giới:
- Module chỉ được dùng các kiểu nằm **trực tiếp** trong package gốc của module khác
  (ví dụ `auth` dùng `user.UserApi`), không được import `*.internal.*` của module khác.
- `platform` không phụ thuộc vào `user`/`auth`.
- `ModularityTest` (Spring Modulith) kiểm tra các quy tắc trên và fail nếu vi phạm.

Luồng một request đã đăng nhập, ví dụ `PUT /api/v1/users/me`:
1. `TraceIdFilter` gắn `X-Trace-Id`; Spring Security xác thực Bearer JWT.
2. `@CurrentUserId Long userId` lấy user ID từ subject của JWT (trả 401 nếu không hợp lệ).
3. `UserController` → `UserService` (transaction) → `UserRepository`.
4. Entity `User` được chuyển thành `UserProfileResponse`; không trả entity ra HTTP.
5. `ApiResponseAdvice` bọc kết quả vào envelope `ApiResponse`.

Khi thêm module mới (ví dụ `lesson`): tạo `com.thanh.workhub.lesson`, đặt code trong `lesson/internal/...`,
chỉ đưa ra package gốc những gì module khác thật sự cần.

## 4. Xử lý lỗi

Mọi lỗi có chủ đích đều ném `ApiException(ErrorCode.XXX)`; danh mục nằm trong `platform/web/ErrorCode`.
Không dùng `ResponseStatusException` hay exception riêng lẻ không có mã.

| `code` | Ý nghĩa |
|---|---|
| `0` | Thành công |
| `400..599` | Lỗi chung, trùng HTTP status (validation, 401, 404, 500...) |
| `1001` | Đổi email cần mật khẩu hiện tại (HTTP 409) |
| `1002` | Mật khẩu hiện tại sai (HTTP 400) |
| `1003` | Email đã được sử dụng (HTTP 409) |
| `1004` | Số điện thoại đã được sử dụng (HTTP 409) |

## 5. Database và migration
- `db/migration/`: schema dùng chung cho mọi môi trường.
- `db/local/`: dữ liệu mẫu (V3, V7), **chỉ** chạy khi bật profile `local`.
- Flyway chạy migration trước khi JPA kiểm tra schema (`ddl-auto: validate`).
- Không sửa migration đã áp dụng; thêm migration phiên bản kế tiếp.
- Sau khi pull code có di chuyển/xóa file migration, chạy `./mvnw clean` để `target/` không còn file cũ.
- Dữ liệu lưu trong Docker named volume. `docker compose down` giữ lại dữ liệu.
- `docker compose down -v` XÓA toàn bộ dữ liệu local; chỉ dùng khi chủ động reset.
- Không dùng cùng database đã seed local cho môi trường khác; tạo database riêng.

## 6. Kiểm thử
Không cần Docker (HTTP, service, ranh giới module):
```bash
./mvnw -Dtest='ModularityTest,UserControllerTest,CorsTest,AuthControllerTest,AuthServiceTest,UserServiceTest' test
```
Toàn bộ test, gồm PostgreSQL thật trong container tạm (Docker bắt buộc):
```bash
./mvnw test
```
`UserDatabaseTest`, `AvatarDatabaseTest` kiểm tra migration + JPA + service + HTTP thực tế.
Test không trỏ vào database học của bạn. Không âm thầm bỏ qua test khi thiếu Docker.

Đóng gói:
```bash
./mvnw clean verify
java -jar target/workhub-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## 7. Cấu hình và kết nối iOS
Các biến môi trường: DB_URL, DB_USERNAME, DB_PASSWORD, SERVER_PORT, SERVER_ADDRESS, JWT_SECRET_BASE64,
CLOUDINARY_ENABLED, CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET, CORS_ALLOWED_ORIGIN_PATTERNS.
Mặc định API chỉ bind 127.0.0.1.
Không triển khai công khai dữ liệu thật bằng cấu hình này.
Simulator trên cùng Mac có thể gọi http://localhost:8081/api/v1/users/1.
Thiết bị iPhone thật cần địa chỉ LAN của Mac; phải chủ động đổi SERVER_ADDRESS,
kiểm tra quyền mạng local/ATS trong app. Bài này chưa thay đổi cấu hình iOS.

Response được bọc trong envelope `ApiResponse`; model Swift cần decode trường `data`.

## 8. Khi gặp lỗi
- Port 5433/8081 bị dùng: đổi ports trong compose / SERVER_PORT, và DB_URL tương ứng.
- Database chưa chạy: `docker compose ps`, `docker compose logs postgres`.
- Permission denied mvnw: `chmod +x mvnw`.
- Invalid target release 21: Maven đang chạy JDK cũ; kiểm tra `./mvnw -v`.
- Máy công ty có Maven settings riêng: `./mvnw -s /duong/dan/settings.xml ...`.
- Could not find a valid Docker environment: mở Docker Desktop trước test tích hợp.

## 9. API cập nhật hồ sơ: `PUT /api/v1/users/me`

Cần header `Authorization: Bearer <access-token>`. User ID lấy từ subject của JWT.

```json
{
  "fullName": "Nguyễn Văn Thành",
  "phone": "0901234567",
  "address": "Hà Nội",
  "gender": "MALE",
  "dateOfBirth": "1995-05-20"
}
```

- `fullName`, `phone`, `address` bắt buộc, tối đa lần lượt 150, 30, 500 ký tự; khoảng trắng đầu/cuối bị bỏ.
- `gender`: `MALE`, `FEMALE`, `OTHER` hoặc `null`. `dateOfBirth`: ngày ISO không ở tương lai, hoặc `null`.
  Bỏ trống trường tùy chọn sẽ xóa giá trị đã lưu.
- Không cập nhật avatar (dùng `POST /api/v1/users/me/avatar`, multipart `file`, JPEG/PNG ≤ 5 MB).
- `email` tùy chọn; bỏ trống thì giữ email cũ. Đổi email cần `currentPassword`:
  thiếu → `1001`, sai → `1002`, email trùng → `1003`. Email mới được lưu chữ thường và đánh dấu chưa xác thực
  (đăng nhập bằng email cần xác thực; đăng nhập bằng số điện thoại vẫn dùng được).
- Số điện thoại thuộc user khác → `1004`. Migration V9 thêm unique constraint cho trường hợp ghi đồng thời.
- Mọi kiểm tra chạy trước khi thay đổi dữ liệu. Thành công trả HTTP 200, hồ sơ mới nằm trong `data`.
