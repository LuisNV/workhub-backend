package com.thanh.workhub.auth.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một thiết bị đang/đã đăng nhập. {@code id} trùng với {@code family_id} của refresh token. */
@Entity
@Table(name = "login_sessions")
public class LoginSession {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "device_id", length = 64)
    private String deviceId;
    @Column(name = "device_name", length = 100)
    private String deviceName;
    @Column(length = 50)
    private String platform;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 30)
    private SessionRevokeReason revokedReason;

    protected LoginSession() {
    }

    public LoginSession(Long userId, DeviceInfo device, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.deviceId = device.deviceId();
        this.deviceName = device.deviceName();
        this.platform = device.platform();
        this.userAgent = device.userAgent();
        this.ipAddress = device.ipAddress();
        this.createdAt = now;
        this.lastActiveAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /** Gọi mỗi lần refresh token: cập nhật lần hoạt động gần nhất, IP và hạn của phiên. */
    public void touch(DeviceInfo device, Instant now, Instant expiresAt) {
        this.lastActiveAt = now;
        this.expiresAt = expiresAt;
        if (device.userAgent() != null) this.userAgent = device.userAgent();
        if (device.ipAddress() != null) this.ipAddress = device.ipAddress();
    }

    public void revoke(SessionRevokeReason reason, Instant now) {
        if (revokedAt != null) return;
        this.revokedAt = now;
        this.revokedReason = reason;
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getPlatform() { return platform; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public SessionRevokeReason getRevokedReason() { return revokedReason; }
}
