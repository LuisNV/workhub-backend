package com.thanh.workhub.auth.internal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Màn "Đổi mật khẩu": mật khẩu hiện tại, mật khẩu mới và lựa chọn đăng xuất khỏi thiết bị khác. */
public record StartPasswordChangeRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
        @NotNull(message = "Vui lòng nhập mật khẩu mới") @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        String newPassword,
        @NotNull(message = "Vui lòng nhập lại mật khẩu mới") String confirmPassword,
        boolean logoutOtherDevices) {

    @Override
    public String toString() {
        return "StartPasswordChangeRequest[redacted]";
    }
}
