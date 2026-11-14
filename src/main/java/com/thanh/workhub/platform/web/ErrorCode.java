package com.thanh.workhub.platform.web;

import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi duy nhất của API.
 *
 * <p>Quy ước trường {@code code} trong {@link ApiResponse}:
 * <ul>
 *   <li>{@code 0}: thành công.</li>
 *   <li>{@code 400..599}: lỗi chung, trùng với HTTP status.</li>
 *   <li>{@code >= 1000}: lỗi nghiệp vụ mà client cần xử lý riêng.</li>
 * </ul>
 */
public enum ErrorCode {

    // Lỗi chung: code = HTTP status
    BAD_REQUEST(400, HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    INVALID_ID(400, HttpStatus.BAD_REQUEST, "ID phải là số nguyên dương"),
    UNAUTHORIZED(401, HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc token không hợp lệ"),
    INVALID_TOKEN_SUBJECT(401, HttpStatus.UNAUTHORIZED, "Token không có user ID hợp lệ"),
    INVALID_CREDENTIALS(401, HttpStatus.UNAUTHORIZED, "Email, số điện thoại hoặc mật khẩu không đúng"),
    INVALID_REFRESH_TOKEN(401, HttpStatus.UNAUTHORIZED, "Refresh Token không hợp lệ hoặc đã hết hạn"),
    FORBIDDEN(403, HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập"),
    EMAIL_NOT_VERIFIED(403, HttpStatus.FORBIDDEN, "Email chưa được xác thực"),
    USER_NOT_FOUND(404, HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    DATA_CONFLICT(409, HttpStatus.CONFLICT, "Thông tin cập nhật bị trùng hoặc không hợp lệ"),
    IMAGE_EMPTY(400, HttpStatus.BAD_REQUEST, "Ảnh không được rỗng"),
    IMAGE_INVALID(400, HttpStatus.BAD_REQUEST, "Chỉ chấp nhận ảnh JPEG hoặc PNG hợp lệ"),
    IMAGE_TOO_MANY_PIXELS(400, HttpStatus.BAD_REQUEST, "Ảnh tối đa 20 megapixel"),
    IMAGE_TOO_LARGE(413, HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh tối đa 5 MB"),
    INTERNAL_ERROR(500, HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống"),
    STORAGE_UPLOAD_FAILED(502, HttpStatus.BAD_GATEWAY, "Không thể upload ảnh lên Cloudinary"),
    STORAGE_DISABLED(503, HttpStatus.SERVICE_UNAVAILABLE, "Cloudinary chưa được bật"),

    // Lỗi nghiệp vụ: code >= 1000
    CURRENT_PASSWORD_REQUIRED(1001, HttpStatus.CONFLICT, "Vui lòng nhập mật khẩu hiện tại để đổi email"),
    CURRENT_PASSWORD_INCORRECT(1002, HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    EMAIL_ALREADY_USED(1003, HttpStatus.CONFLICT, "Email đã được sử dụng"),
    PHONE_ALREADY_USED(1004, HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");

    private final int code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
