package com.thanh.workhub.user;

import java.util.Optional;

/**
 * API công khai của module user. Module khác (auth, ...) chỉ được dùng các kiểu trong package
 * {@code com.thanh.workhub.user}, không được import {@code user.internal.*}.
 */
public interface UserApi {

    /** Tìm thông tin đăng nhập theo email (đã chuẩn hóa chữ thường). */
    Optional<UserCredentials> findCredentialsByEmail(String email);

    /** Tìm thông tin đăng nhập theo số điện thoại (đã bỏ mọi khoảng trắng). */
    Optional<UserCredentials> findCredentialsByPhone(String phone);

    Optional<UserCredentials> findCredentialsById(Long id);

    /** Trả hồ sơ người dùng; ném lỗi USER_NOT_FOUND nếu không tồn tại. */
    UserProfileResponse getProfile(Long id);

    /**
     * Tạo user đã xác thực email. Ném EMAIL_ALREADY_USED / PHONE_ALREADY_USED nếu bị trùng
     * (kể cả khi hai request đăng ký chạy song song).
     */
    UserProfileResponse register(NewUser newUser);

    /**
     * Kiểm tra mật khẩu hiện tại, có giới hạn số lần sai (dùng chung mọi chức năng cần nhập lại mật khẩu).
     * Ném CURRENT_PASSWORD_INCORRECT ("Bạn còn N lần thử") hoặc PASSWORD_CHECK_LOCKED. Lần sai vẫn được ghi lại
     * dù transaction của nơi gọi rollback.
     */
    void verifyCurrentPassword(Long userId, String rawPassword);

    /** Thay hash mật khẩu. Ném USER_NOT_FOUND nếu user không tồn tại. */
    void changePassword(Long userId, String newPasswordHash);
}
