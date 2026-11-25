package com.thanh.workhub.auth.internal.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bước 2: tạo mật khẩu. Server gửi mã xác thực về email sau bước này. */
public record InitiateRegistrationRequest(
        @NotNull(message = "Mật khẩu không được để trống") @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        String password,
        @NotNull(message = "Vui lòng nhập lại mật khẩu") String confirmPassword,
        boolean biometricEnabled) {

    @Override
    public String toString() {
        return "InitiateRegistrationRequest[redacted]";
    }
}
