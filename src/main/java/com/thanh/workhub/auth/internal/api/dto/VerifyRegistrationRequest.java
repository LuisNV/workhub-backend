package com.thanh.workhub.auth.internal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Bước 3: mã 6 số nhận qua email. */
public record VerifyRegistrationRequest(
        @NotBlank(message = "Vui lòng nhập mã xác thực")
        @Pattern(regexp = "\\d{6}", message = "Mã xác thực gồm 6 chữ số") String code) {
}
