package com.thanh.workhub.auth.internal.api.dto;

import java.time.Instant;

/**
 * Nhập đúng mã → màn 3 "Mật khẩu mới". {@code resetToken} dùng một lần, gửi kèm khi đặt mật khẩu mới;
 * client chỉ giữ trong bộ nhớ, không lưu xuống máy.
 */
public record PasswordResetVerifiedResponse(String resetToken, Instant resetTokenExpiresAt, String email) {

    @Override
    public String toString() {
        return "PasswordResetVerifiedResponse[resetToken=redacted, expiresAt=" + resetTokenExpiresAt + "]";
    }
}
