package com.thanh.workhub.auth.internal.domain;

/**
 * Trạng thái phiên đăng ký.
 * <ul>
 *   <li>{@code CREATED}: đã nhập thông tin (bước 1), chưa gửi mã.</li>
 *   <li>{@code OTP_SENT}: đã đặt mật khẩu và gửi mã về email (bước 2 → 3).</li>
 *   <li>{@code VERIFIED}: email đã xác thực, chờ hoàn tất.</li>
 *   <li>{@code COMPLETED}: đã tạo user, phiên không dùng được nữa.</li>
 * </ul>
 */
public enum RegistrationStatus {
    CREATED, OTP_SENT, VERIFIED, COMPLETED
}
