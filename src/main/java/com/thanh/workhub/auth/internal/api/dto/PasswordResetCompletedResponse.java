package com.thanh.workhub.auth.internal.api.dto;

/**
 * Màn 4 "Đã đặt lại mật khẩu": mọi thiết bị đã đăng xuất ({@code revokedSessionCount}), email thông báo gửi
 * tới {@code notifiedEmail} (đã che bớt). {@code biometricDisabled = true}: client xóa thông tin Face ID /
 * vân tay đã lưu, người dùng bật lại sau khi đăng nhập bằng mật khẩu mới.
 */
public record PasswordResetCompletedResponse(int revokedSessionCount, String notifiedEmail, boolean biometricDisabled) {
}
