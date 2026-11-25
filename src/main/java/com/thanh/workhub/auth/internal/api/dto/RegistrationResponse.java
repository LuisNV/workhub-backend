package com.thanh.workhub.auth.internal.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trạng thái phiên đăng ký. {@code nextStep} cho client biết cần hiện màn nào:
 * SET_PASSWORD → VERIFY_EMAIL → COMPLETE → DONE.
 */
public record RegistrationResponse(
        UUID registrationId,
        String status,
        String nextStep,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        boolean biometricEnabled,
        boolean emailVerified,
        Instant expiresAt,
        Instant otpExpiresAt,
        Instant resendAvailableAt,
        Integer remainingAttempts) {
}
