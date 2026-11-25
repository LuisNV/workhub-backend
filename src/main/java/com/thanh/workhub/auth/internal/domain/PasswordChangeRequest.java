package com.thanh.workhub.auth.internal.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Yêu cầu đổi mật khẩu đang chờ xác minh mã OTP qua email. */
@Entity
@Table(name = "password_change_requests")
public class PasswordChangeRequest {
    public enum Status { PENDING, COMPLETED, CANCELLED }

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "new_password_hash", nullable = false)
    private String newPasswordHash;
    @Column(name = "current_password_hash", nullable = false)
    private String currentPasswordHash;
    @Column(name = "logout_other_devices", nullable = false)
    private boolean logoutOtherDevices;
    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;
    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;
    @Column(name = "otp_sent_at", nullable = false)
    private Instant otpSentAt;
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;
    @Column(name = "otp_send_count", nullable = false)
    private int otpSendCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected PasswordChangeRequest() {
    }

    public PasswordChangeRequest(Long userId, String email, String newPasswordHash, String currentPasswordHash,
            boolean logoutOtherDevices, UUID sessionId, Instant now, Duration ttl) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.status = Status.PENDING;
        this.email = email;
        this.newPasswordHash = newPasswordHash;
        this.currentPasswordHash = currentPasswordHash;
        this.logoutOtherDevices = logoutOtherDevices;
        this.sessionId = sessionId;
        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
    }

    /** Ghi nhận mã mới vừa gửi; mã cũ mất hiệu lực, số lần nhập sai về 0. */
    public void otpIssued(String otpHash, Instant now, Duration otpTtl) {
        this.otpHash = otpHash;
        this.otpSentAt = now;
        this.otpExpiresAt = now.plus(otpTtl);
        this.otpAttempts = 0;
        this.otpSendCount++;
    }

    public void otpFailed() {
        this.otpAttempts++;
    }

    public void complete(Instant now) {
        this.status = Status.COMPLETED;
        this.completedAt = now;
    }

    public void cancel() {
        if (status == Status.PENDING) this.status = Status.CANCELLED;
    }

    public boolean isPending(Instant now) {
        return status == Status.PENDING && now.isBefore(expiresAt);
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public Status getStatus() { return status; }
    public String getEmail() { return email; }
    public String getNewPasswordHash() { return newPasswordHash; }
    public String getCurrentPasswordHash() { return currentPasswordHash; }
    public boolean isLogoutOtherDevices() { return logoutOtherDevices; }
    public UUID getSessionId() { return sessionId; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public Instant getOtpSentAt() { return otpSentAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public int getOtpSendCount() { return otpSendCount; }
    public Instant getExpiresAt() { return expiresAt; }
}
