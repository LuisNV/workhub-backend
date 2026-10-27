# Workhub Backend — Bài 1: API đọc hồ sơ

Java 21 · Spring Boot 3.5.14 · Maven · PostgreSQL 17 · Flyway.
Một ứng dụng chia theo module nghiệp vụ. Hiện chỉ có module user và xử lý lỗi HTTP;
chưa thêm đăng nhập, API ghi, Redis, Kafka hoặc các module rỗng.

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
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
Windows: dùng `mvnw.cmd` thay `./mvnw`.
Nếu đã cài Maven có thể dùng `mvn` thay wrapper.

API ở http://localhost:8081. Database ở localhost:5433, database/user `workhub`,
password `workhub_local` (chỉ dùng học trên máy cá nhân).

```bash
curl -i http://localhost:8081/api/v1/users/1
curl -i http://localhost:8081/api/v1/users/999
curl -i http://localhost:8081/api/v1/users/abc
curl -i http://localhost:8081/api/v1/users/0
curl -i http://localhost:8081/actuator/health
```

Kết quả thành công HTTP 200:
```json
{"id":1,"fullName":"Nguyễn Văn Thành","email":"thanh.nguyen@example.com","phone":"090 123 4567","address":"Hà Nội, Việt Nam"}
```
ID không có: 404, body ProblemDetail có `code: USER_NOT_FOUND`.
ID không phải Long hoặc <= 0: 400. POST trên endpoint này: 405.
`requests.http` chứa các request để thử trong IDE hỗ trợ HTTP Client.

## 3. Hiểu code theo luồng
Các đường dẫn Java bên dưới đều trong `src/main/java/com/thanh/workhub/`:
1. `WorkhubApplication`: khởi động, quét component bên dưới package gốc.
2. `user/internal/api/UserController`: nhận ID từ URL, gọi service.
3. `user/internal/application/UserService`: transaction chỉ đọc, tìm user và tạo DTO.
4. `user/internal/persistence/UserRepository`: Spring Data tạo truy vấn theo ID.
5. `user/internal/domain/User`: ánh xạ bảng `app_users`, không trả entity ra HTTP.
6. `user/internal/api/dto/UserProfileResponse`: JSON trả về màn hình mobile.
7. `platform/web/GlobalExceptionHandler`: lỗi không tìm thấy thành 404; kế thừa xử lý lỗi MVC chuẩn.

Đây là khung tối thiểu theo module, chưa phải bộ framework enterprise hoàn chỉnh.
DTO hiện phục vụ một use case nên mapper đặt ngay trong service. Chỉ tách mapper/facade
khi có nhu cầu. Khi có module thứ hai, bổ sung API module và test ranh giới module.
Thư mục `internal` thể hiện ý định tổ chức, không tự chặn truy cập bằng Java.

## 4. Database và migration
- `db/migration/V1__create_app_users.sql`: schema dùng chung.
- `db/local/V2__seed_demo_user.sql`: dữ liệu mẫu chỉ cho profile `local`.
- Flyway chạy migration trước khi JPA kiểm tra schema (`ddl-auto: validate`).
- Không sửa migration đã áp dụng; thêm migration phiên bản kế tiếp.
- Dữ liệu lưu trong Docker named volume. `docker compose down` giữ lại dữ liệu.
- `docker compose down -v` XÓA toàn bộ dữ liệu local; chỉ dùng khi chủ động reset.
- Không dùng cùng database đã seed local cho môi trường khác; tạo database riêng.

## 5. Kiểm thử
Chỉ kiểm thử HTTP, không cần Docker:
```bash
./mvnw -Dtest=UserControllerTest test
```
Toàn bộ test, gồm PostgreSQL thật trong container tạm (Docker bắt buộc):
```bash
./mvnw test
```
`UserDatabaseTest` kiểm tra migration + JPA + service + HTTP thực tế.
Test không trỏ vào database học của bạn. Không âm thầm bỏ qua test khi thiếu Docker.

Đóng gói:
```bash
./mvnw clean verify
java -jar target/workhub-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## 6. Cấu hình và kết nối iOS
Các biến môi trường: DB_URL, DB_USERNAME, DB_PASSWORD, SERVER_PORT, SERVER_ADDRESS.
Mặc định API chỉ bind 127.0.0.1 vì bản học này CHƯA CÓ XÁC THỰC.
Không triển khai công khai dữ liệu thật bằng cấu hình này.
Simulator trên cùng Mac có thể gọi http://localhost:8081/api/v1/users/1.
Thiết bị iPhone thật cần địa chỉ LAN của Mac; phải chủ động đổi SERVER_ADDRESS,
kiểm tra quyền mạng local/ATS trong app. Bài này chưa thay đổi cấu hình iOS.

Response JSON trực tiếp, không bọc BaseResponse. ProfileUser Swift chỉ cần Decodable
với fullName/email/phone/address; trường id dư được JSONDecoder bỏ qua nếu model không khai báo.

## 7. Khi gặp lỗi
- Port 5433/8081 bị dùng: đổi ports trong compose / SERVER_PORT, và DB_URL tương ứng.
- Database chưa chạy: `docker compose ps`, `docker compose logs postgres`.
- Permission denied mvnw: `chmod +x mvnw`.
- Invalid target release 21: Maven đang chạy JDK cũ; kiểm tra `./mvnw -v`.
- Máy công ty có Maven settings riêng: `./mvnw -s /duong/dan/settings.xml ...`.
- Could not find a valid Docker environment: mở Docker Desktop trước test tích hợp.

## 8. Kết quả kiểm tra khi tạo project
- Maven biên dịch source chính và source test thành công bằng JDK 17 với override `-Djava.version=17`.
- `UserControllerTest`: 4 test, 0 lỗi, 0 bỏ qua (bao gồm 200, 404, 400, 405).
- Chưa chạy với JDK 21 và chưa chạy `UserDatabaseTest` vì môi trường tạo file không có Docker.
- pom.xml bàn giao giữ Java 21. Trên máy bạn hãy chạy `./mvnw test` với JDK 21 và Docker.
- Mockito dùng subclass mock maker để không cần Java agent tự attach; không mock class final/static.
