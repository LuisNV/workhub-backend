package com.thanh.workhub.auth.internal.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Thông tin thiết bị không bắt buộc:
 * {@code deviceId} là UUID app tự sinh và giữ cố định để nhận ra cùng một máy;
 * {@code deviceName}/{@code platform} để hiện trong danh sách thiết bị ("iPhone 15 Pro" / "iOS 18.1").
 */
public record LoginRequest(

    @NotBlank(message = "Email hoặc số điện thoại không được để trống")
    @JsonAlias({"email", "phone"})
    String identifier,

    @NotBlank(message = "Mật khẩu không được để trống")
    String password,

    @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}", message = "deviceId gồm 8–64 ký tự chữ, số hoặc . _ : -")
    String deviceId,

    @Size(max = 100, message = "Tên thiết bị tối đa 100 ký tự")
    String deviceName,

    @Size(max = 50, message = "Nền tảng tối đa 50 ký tự")
    String platform

) {
    public LoginRequest(String identifier, String password) {
        this(identifier, password, null, null, null);
    }

    @Override
    public String toString() {
        return "LoginRequest[identifier=" + identifier + ", password=redacted]";
    }
}
