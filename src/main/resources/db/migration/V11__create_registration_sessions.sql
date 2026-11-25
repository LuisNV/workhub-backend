-- Phiên đăng ký nhiều bước: thông tin -> mật khẩu + gửi OTP -> xác thực email -> hoàn tất.
-- User chỉ được tạo trong app_users ở bước hoàn tất, nên phiên bỏ dở không để lại tài khoản rác.
CREATE TABLE registration_sessions (
    id                UUID PRIMARY KEY,
    status            VARCHAR(20)  NOT NULL,
    full_name         VARCHAR(150) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    phone             VARCHAR(30)  NOT NULL,
    date_of_birth     DATE,
    password_hash     VARCHAR(255),
    biometric_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    otp_hash          VARCHAR(64),
    otp_expires_at    TIMESTAMPTZ,
    otp_sent_at       TIMESTAMPTZ,
    otp_attempts      INT          NOT NULL DEFAULT 0,
    otp_send_count    INT          NOT NULL DEFAULT 0,
    email_verified_at TIMESTAMPTZ,
    user_id           BIGINT REFERENCES app_users (id),
    created_at        TIMESTAMPTZ  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_registration_sessions_status
        CHECK (status IN ('CREATED', 'OTP_SENT', 'VERIFIED', 'COMPLETED'))
);

CREATE INDEX idx_registration_sessions_expires_at ON registration_sessions (expires_at);

-- Nhật ký gửi mã theo email (qua mọi phiên), để giới hạn số mail gửi tới một địa chỉ trong 1 giờ.
CREATE TABLE registration_otp_sends (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email   VARCHAR(254) NOT NULL,
    sent_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_registration_otp_sends_email_sent_at ON registration_otp_sends (email, sent_at);
