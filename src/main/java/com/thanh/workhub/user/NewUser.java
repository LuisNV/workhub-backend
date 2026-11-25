package com.thanh.workhub.user;

import java.time.LocalDate;

/**
 * Dữ liệu tạo user mới từ luồng đăng ký. Email đã chuẩn hóa chữ thường, phone đã chuẩn hóa chỉ còn chữ số,
 * email đã được xác thực bằng OTP trước khi gọi.
 */
public record NewUser(String fullName, String email, String phone, LocalDate dateOfBirth, String passwordHash) {

    @Override
    public String toString() {
        return "NewUser[email=" + email + ", passwordHash=redacted]";
    }
}
