package com.thanh.workhub.auth.internal.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Màn 2 "Kiểm tra email". Luôn trả về như nhau dù tài khoản có tồn tại hay không.
 * {@code identifierType} = EMAIL | PHONE; {@code destination} là chính thứ người dùng đã nhập, đã che bớt
 * ("mi•••••@email.com" / "090•••••67"). Với PHONE, mã được gửi tới email đã đăng ký với số đó.
 */
public record PasswordResetResponse(
        UUID resetId,
        String identifierType,
        String destination,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        int remainingAttempts,
        Instant expiresAt) {
}
