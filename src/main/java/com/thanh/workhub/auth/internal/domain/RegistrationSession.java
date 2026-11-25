package com.thanh.workhub.auth.internal.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Phiên đăng ký nhiều bước. Chỉ lưu hash của mật khẩu và mã OTP, không lưu bản gốc. */
@Entity
@Table(name = "registration_sessions")
public class RegistrationSession {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 30)
    private String phone;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "biometric_enabled", nullable = false)
    private boolean biometricEnabled;
    @Column(name = "otp_hash", length = 64)
    private String otpHash;
    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;
    @Column(name = "otp_sent_at")
    private Instant otpSentAt;
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;
    @Column(name = "otp_send_count", nullable = false)
    private int otpSendCount;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RegistrationSession() {
    }

    public static RegistrationSession start(String fullName, String email, String phone, LocalDate dateOfBirth,
            Instant now, Duration ttl) {
        var session = new RegistrationSession();
        session.id = UUID.randomUUID();
        session.status = RegistrationStatus.CREATED;
        session.fullName = fullName;
        session.email = email;
        session.phone = phone;
        session.dateOfBirth = dateOfBirth;
        session.createdAt = now;
        session.expiresAt = now.plus(ttl);
        return session;
    }

    /** Phiên phải còn hạn và chưa hoàn tất thì mới được thao tác tiếp. */
    public void ensureUsable(Instant now) {
        if (status == RegistrationStatus.COMPLETED) throw new ApiException(ErrorCode.REGISTRATION_INVALID_STEP,
                "Phiên đăng ký đã hoàn tất, vui lòng đăng nhập");
        if (!now.isBefore(expiresAt)) throw new ApiException(ErrorCode.REGISTRATION_EXPIRED);
    }

    public void ensureStatus(RegistrationStatus... allowed) {
        for (var candidate : allowed) {
            if (status == candidate) return;
        }
        throw new ApiException(ErrorCode.REGISTRATION_INVALID_STEP);
    }

    public void setPassword(String passwordHash, boolean biometricEnabled) {
        ensureStatus(RegistrationStatus.CREATED, RegistrationStatus.OTP_SENT);
        this.passwordHash = passwordHash;
        this.biometricEnabled = biometricEnabled;
    }

    /** Đổi email trước khi xác thực: mã cũ (gửi tới email cũ) bị hủy. */
    public void changeEmail(String email) {
        ensureStatus(RegistrationStatus.CREATED, RegistrationStatus.OTP_SENT);
        this.email = email;
        this.status = RegistrationStatus.CREATED;
        this.otpHash = null;
        this.otpExpiresAt = null;
        this.otpAttempts = 0;
    }

    public void otpIssued(String otpHash, Instant now, Duration otpTtl) {
        if (passwordHash == null) throw new ApiException(ErrorCode.REGISTRATION_INVALID_STEP,
                "Vui lòng tạo mật khẩu trước khi gửi mã xác thực");
        this.otpHash = otpHash;
        this.otpSentAt = now;
        this.otpExpiresAt = now.plus(otpTtl);
        this.otpAttempts = 0;
        this.otpSendCount++;
        this.status = RegistrationStatus.OTP_SENT;
    }

    public void otpFailed() {
        this.otpAttempts++;
    }

    public void markVerified(Instant now) {
        this.status = RegistrationStatus.VERIFIED;
        this.emailVerifiedAt = now;
        this.otpHash = null;
        this.otpExpiresAt = null;
    }

    public void complete(Long userId) {
        ensureStatus(RegistrationStatus.VERIFIED);
        this.status = RegistrationStatus.COMPLETED;
        this.userId = userId;
    }

    public UUID getId() { return id; }
    public RegistrationStatus getStatus() { return status; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isBiometricEnabled() { return biometricEnabled; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public Instant getOtpSentAt() { return otpSentAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public int getOtpSendCount() { return otpSendCount; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
