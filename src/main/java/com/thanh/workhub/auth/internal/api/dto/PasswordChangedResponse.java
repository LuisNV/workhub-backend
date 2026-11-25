package com.thanh.workhub.auth.internal.api.dto;

/**
 * Màn "Đã đổi mật khẩu". Thiết bị đang dùng luôn giữ đăng nhập; {@code revokedSessionCount} là số
 * thiết bị khác đã bị đăng xuất.
 */
public record PasswordChangedResponse(boolean loggedOutOtherDevices, int revokedSessionCount) {
}
