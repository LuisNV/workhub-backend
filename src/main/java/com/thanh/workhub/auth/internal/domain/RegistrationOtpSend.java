package com.thanh.workhub.auth.internal.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Một lần gửi mã OTP tới một email, dùng để giới hạn tần suất theo email. */
@Entity
@Table(name = "registration_otp_sends")
public class RegistrationOtpSend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected RegistrationOtpSend() {
    }

    public RegistrationOtpSend(String email, Instant sentAt) {
        this.email = email;
        this.sentAt = sentAt;
    }
}
