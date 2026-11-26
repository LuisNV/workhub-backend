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

/**
 * Yêu cầu đặt lại mật khẩu ("Quên mật khẩu"). {@code userId == null} nghĩa là không có tài khoản ứng với
 * email/số điện thoại đã nhập: yêu cầu vẫn được tạo để phản hồi giống hệt, nhưng không gửi mail và mã
 * không bao giờ đúng.
 */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequest {
    public enum Status { PENDING, VERIFIED, COMPLETED, CANCELLED }

    @Id
    private UUID id;
    @Column(nullable = false, length = 254)
    private String identifier;
    @Column(name = "user_id")
    private Long userId;
    @Column(length = 254)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
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
    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(String identifier, Long userId, String email, String ipAddress,
            Instant now, Duration ttl) {
        this.id = UUID.randomUUID();
        this.identifier = identifier;
        this.userId = userId;
        this.email = email;
        this.ipAddress = ipAddress;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.expiresAt = now.plus(ttl);
    }

    /** Ghi nhận mã mới; mã cũ mất hiệu lực, số lần nhập sai về 0. */
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

    /**
     * Nhập đúng mã: cấp token đặt mật khẩu, yêu cầu còn hiệu lực thêm {@code tokenTtl}. Gọi lại (client gửi
     * lại request) thì token cũ bị thay.
     */
    public void verified(String resetTokenHash, Instant now, Duration tokenTtl) {
        this.status = Status.VERIFIED;
        this.resetTokenHash = resetTokenHash;
        this.verifiedAt = now;
        this.expiresAt = now.plus(tokenTtl);
    }

    public void complete(Instant now) {
        this.status = Status.COMPLETED;
        this.completedAt = now;
        this.resetTokenHash = null;
    }

    public void cancel() {
        if (status == Status.PENDING || status == Status.VERIFIED) {
            this.status = Status.CANCELLED;
            this.resetTokenHash = null;
        }
    }

    /** Còn dùng được (chờ nhập mã hoặc chờ đặt mật khẩu) và chưa hết hạn. */
    public boolean isOpen(Instant now) {
        return (status == Status.PENDING || status == Status.VERIFIED) && now.isBefore(expiresAt);
    }

    public UUID getId() { return id; }
    public String getIdentifier() { return identifier; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public Status getStatus() { return status; }
    public String getOtpHash() { return otpHash; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public Instant getOtpSentAt() { return otpSentAt; }
    public int getOtpAttempts() { return otpAttempts; }
    public int getOtpSendCount() { return otpSendCount; }
    public String getResetTokenHash() { return resetTokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
}
