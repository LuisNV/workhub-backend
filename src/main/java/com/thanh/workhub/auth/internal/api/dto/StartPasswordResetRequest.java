package com.thanh.workhub.auth.internal.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Màn 1 "Đặt lại mật khẩu": email hoặc số điện thoại của tài khoản. */
public record StartPasswordResetRequest(
        @NotBlank(message = "Vui lòng nhập email hoặc số điện thoại")
        @Size(max = 254, message = "Email hoặc số điện thoại không hợp lệ")
        @JsonAlias({"email", "phone"})
        String identifier) {
}
