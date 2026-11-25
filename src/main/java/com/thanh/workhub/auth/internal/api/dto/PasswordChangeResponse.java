package com.thanh.workhub.auth.internal.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Đã gửi mã xác minh, chờ người dùng nhập (màn "Xác minh email"). */
public record PasswordChangeResponse(
        UUID requestId,
        String email,
        boolean logoutOtherDevices,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        int remainingAttempts,
        Instant expiresAt) {
}
