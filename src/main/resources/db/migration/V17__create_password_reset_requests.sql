-- Quên mật khẩu (chưa đăng nhập): nhập email/số điện thoại → nhập mã OTP gửi qua email → đặt mật khẩu mới.
-- Yêu cầu được tạo kể cả khi không có tài khoản (user_id NULL, không gửi mail) để phản hồi giống hệt nhau,
-- không cho dò xem email/số điện thoại nào đã đăng ký.
CREATE TABLE password_reset_requests (
    id                UUID PRIMARY KEY,
    -- Email (chữ thường) hoặc số điện thoại (chỉ chữ số) người dùng đã nhập.
    identifier        VARCHAR(254) NOT NULL,
    user_id           BIGINT REFERENCES app_users (id),
    -- Email nhận mã tại thời điểm tạo yêu cầu; email tài khoản đổi trong lúc chờ thì yêu cầu mất hiệu lực.
    email             VARCHAR(254),
    status            VARCHAR(20)  NOT NULL,
    otp_hash          VARCHAR(64)  NOT NULL,
    otp_expires_at    TIMESTAMPTZ  NOT NULL,
    otp_sent_at       TIMESTAMPTZ  NOT NULL,
    otp_attempts      INT          NOT NULL DEFAULT 0,
    otp_send_count    INT          NOT NULL DEFAULT 1,
    -- Sau khi nhập đúng mã: SHA-256 của token một lần dùng để đặt mật khẩu mới.
    reset_token_hash  VARCHAR(64),
    ip_address        VARCHAR(64),
    created_at        TIMESTAMPTZ  NOT NULL,
    verified_at       TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ  NOT NULL,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT ck_password_reset_requests_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_password_reset_requests_identifier ON password_reset_requests (identifier, created_at);
CREATE INDEX idx_password_reset_requests_ip ON password_reset_requests (ip_address, created_at);
CREATE INDEX idx_password_reset_requests_user ON password_reset_requests (user_id) WHERE user_id IS NOT NULL;

-- Đặt lại mật khẩu đăng xuất mọi thiết bị.
ALTER TABLE login_sessions
    DROP CONSTRAINT ck_login_sessions_revoked_reason,
    ADD CONSTRAINT ck_login_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'REVOKED', 'EVICTED', 'TOKEN_REUSE', 'REPLACED', 'PASSWORD_RESET'));
