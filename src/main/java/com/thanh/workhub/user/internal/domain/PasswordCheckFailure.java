package com.thanh.workhub.user.internal.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một lần nhập sai mật khẩu hiện tại. */
@Entity
@Table(name = "password_check_failures")
public class PasswordCheckFailure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    protected PasswordCheckFailure() {
    }

    public PasswordCheckFailure(Long userId, Instant failedAt) {
        this.userId = userId;
        this.failedAt = failedAt;
    }

    public Instant getFailedAt() { return failedAt; }
}
