package com.thanh.workhub.auth.internal.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body không bắt buộc của bước hoàn tất: thông tin thiết bị cho phiên đăng nhập đầu tiên. */
public record CompleteRegistrationRequest(
        @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}", message = "deviceId gồm 8–64 ký tự chữ, số hoặc . _ : -")
        String deviceId,
        @Size(max = 100, message = "Tên thiết bị tối đa 100 ký tự") String deviceName,
        @Size(max = 50, message = "Nền tảng tối đa 50 ký tự") String platform) {
}
