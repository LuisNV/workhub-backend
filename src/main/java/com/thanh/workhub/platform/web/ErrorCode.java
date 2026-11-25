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
    MAIL_SEND_FAILED(502, HttpStatus.BAD_GATEWAY, "Không thể gửi email, vui lòng thử lại"),
    MAIL_DISABLED(503, HttpStatus.SERVICE_UNAVAILABLE, "Chức năng gửi email chưa được bật"),

    // Lỗi nghiệp vụ: code >= 1000
    CURRENT_PASSWORD_REQUIRED(1001, HttpStatus.CONFLICT, "Vui lòng nhập mật khẩu hiện tại để đổi email"),
    CURRENT_PASSWORD_INCORRECT(1002, HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    EMAIL_ALREADY_USED(1003, HttpStatus.CONFLICT, "Email đã được sử dụng"),
    PHONE_ALREADY_USED(1004, HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng"),

    // Đăng ký tài khoản
    REGISTRATION_NOT_FOUND(1005, HttpStatus.NOT_FOUND, "Không tìm thấy phiên đăng ký"),
    REGISTRATION_EXPIRED(1006, HttpStatus.GONE, "Phiên đăng ký đã hết hạn, vui lòng đăng ký lại"),
    REGISTRATION_INVALID_STEP(1007, HttpStatus.CONFLICT, "Bước đăng ký không hợp lệ"),
    PASSWORD_CONFIRMATION_MISMATCH(1008, HttpStatus.BAD_REQUEST, "Mật khẩu nhập lại không khớp"),
    PASSWORD_TOO_WEAK(1009, HttpStatus.BAD_REQUEST,
            "Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường và ít nhất 1 chữ số"),
    OTP_INVALID(1010, HttpStatus.BAD_REQUEST, "Mã xác thực không đúng"),
    OTP_EXPIRED(1011, HttpStatus.BAD_REQUEST, "Mã xác thực đã hết hạn, vui lòng gửi lại mã"),
    OTP_TOO_MANY_ATTEMPTS(1012, HttpStatus.TOO_MANY_REQUESTS, "Nhập sai quá nhiều lần, vui lòng gửi lại mã mới"),
    OTP_RESEND_TOO_SOON(1013, HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ trước khi gửi lại mã"),
    OTP_SEND_LIMIT_REACHED(1014, HttpStatus.TOO_MANY_REQUESTS,
            "Đã gửi mã quá nhiều lần, vui lòng tạo phiên đăng ký mới"),

    // Thiết bị đăng nhập
    SESSION_NOT_FOUND(1015, HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị đăng nhập"),
    SESSION_UNKNOWN(1016, HttpStatus.BAD_REQUEST,
            "Không xác định được thiết bị hiện tại, vui lòng đăng nhập lại");

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
