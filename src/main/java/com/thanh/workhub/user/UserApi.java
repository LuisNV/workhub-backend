package com.thanh.workhub.user;

import java.util.Optional;

/**
 * API công khai của module user. Module khác (auth, ...) chỉ được dùng các kiểu trong package
 * {@code com.thanh.workhub.user}, không được import {@code user.internal.*}.
 */
public interface UserApi {

    /** Tìm thông tin đăng nhập theo email (đã chuẩn hóa chữ thường). */
    Optional<UserCredentials> findCredentialsByEmail(String email);

    /** Tìm thông tin đăng nhập theo số điện thoại (đã bỏ khoảng trắng đầu/cuối). */
    Optional<UserCredentials> findCredentialsByPhone(String phone);

    Optional<UserCredentials> findCredentialsById(Long id);

    /** Trả hồ sơ người dùng; ném lỗi USER_NOT_FOUND nếu không tồn tại. */
    UserProfileResponse getProfile(Long id);
}
