package com.thanh.workhub.auth.internal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Màn 3 "Mật khẩu mới". */
public record CompletePasswordResetRequest(
        @NotBlank(message = "Phiên đặt lại mật khẩu không hợp lệ") @Size(max = 128) String resetToken,
        @NotNull(message = "Vui lòng nhập mật khẩu mới") @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        String newPassword,
        @NotNull(message = "Vui lòng nhập lại mật khẩu mới") String confirmPassword) {

    @Override
    public String toString() {
        return "CompletePasswordResetRequest[redacted]";
    }
}
