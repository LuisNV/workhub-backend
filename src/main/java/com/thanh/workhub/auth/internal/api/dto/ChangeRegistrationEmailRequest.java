package com.thanh.workhub.auth.internal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Đổi địa chỉ email" ở màn xác thực. */
public record ChangeRegistrationEmailRequest(
        @NotBlank(message = "Email không được để trống") @Email(message = "Email không hợp lệ")
        @Size(max = 254) String email) {
}
