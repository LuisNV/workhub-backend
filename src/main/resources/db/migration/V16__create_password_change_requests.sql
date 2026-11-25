-- Yêu cầu đổi mật khẩu: đã kiểm tra mật khẩu hiện tại, chờ xác minh mã OTP gửi qua email.
-- Mật khẩu mới chỉ được áp dụng sau khi nhập đúng mã.
CREATE TABLE password_change_requests (
    id                    UUID PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES app_users (id),
    status                VARCHAR(20)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    new_password_hash     VARCHAR(255) NOT NULL,
    -- Hash mật khẩu lúc tạo yêu cầu: nếu mật khẩu bị đổi bằng cách khác trong lúc chờ thì yêu cầu mất hiệu lực.
    current_password_hash VARCHAR(255) NOT NULL,
    logout_other_devices  BOOLEAN      NOT NULL,
    -- Thiết bị gửi yêu cầu: được giữ đăng nhập khi đăng xuất các thiết bị khác.
    session_id            UUID,
    otp_hash              VARCHAR(64)  NOT NULL,
    otp_expires_at        TIMESTAMPTZ  NOT NULL,
    otp_sent_at           TIMESTAMPTZ  NOT NULL,
    otp_attempts          INT          NOT NULL DEFAULT 0,
    otp_send_count        INT          NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ  NOT NULL,
    expires_at            TIMESTAMPTZ  NOT NULL,
    completed_at          TIMESTAMPTZ,
    CONSTRAINT ck_password_change_requests_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_password_change_requests_user ON password_change_requests (user_id, created_at);

-- Mỗi lần nhập sai mật khẩu hiện tại, để giới hạn số lần thử (chống dò mật khẩu bằng token bị lộ).
CREATE TABLE password_check_failures (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id   BIGINT      NOT NULL REFERENCES app_users (id),
    failed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_password_check_failures_user ON password_check_failures (user_id, failed_at);
