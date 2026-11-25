package com.thanh.workhub.auth.internal.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Bước 1: thông tin tài khoản. Số điện thoại cho phép khoảng trắng ("091 234 5678"). */
public record StartRegistrationRequest(
        @NotBlank(message = "Họ tên không được để trống") @Size(max = 150) String fullName,
        @NotBlank(message = "Email không được để trống") @Email(message = "Email không hợp lệ")
        @Size(max = 254) String email,
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^\\s*0(\\s*\\d){9}\\s*$",
                message = "Số điện thoại cần đủ 10 chữ số, bắt đầu bằng 0.") String phone,
        @Past(message = "Ngày sinh phải ở trong quá khứ") LocalDate dateOfBirth) {
}
