-- Mỗi lần đăng nhập trên một thiết bị là một phiên. id của phiên chính là family_id của refresh token:
-- refresh xoay vòng token nhưng giữ nguyên family, nên cả chuỗi token thuộc cùng một phiên.
CREATE TABLE login_sessions (
    id             UUID PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES app_users (id),
    device_name    VARCHAR(100),
    platform       VARCHAR(50),
    user_agent     VARCHAR(512),
    ip_address     VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    last_active_at TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(30),
    CONSTRAINT ck_login_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'REVOKED', 'EVICTED', 'TOKEN_REUSE'))
);

CREATE INDEX idx_login_sessions_user_active ON login_sessions (user_id) WHERE revoked_at IS NULL;

-- Chuyển các lần đăng nhập đã có thành phiên (không rõ thiết bị) để người dùng không bị đăng xuất.
-- Refresh token sống 7 ngày, nên thời điểm tạo/hoạt động được suy ra từ expires_at - 7 ngày.
INSERT INTO login_sessions (id, user_id, created_at, last_active_at, expires_at, revoked_at, revoked_reason)
SELECT family_id,
       user_id,
       MIN(expires_at) - INTERVAL '7 days',
       MAX(expires_at) - INTERVAL '7 days',
       MAX(expires_at),
       CASE WHEN bool_and(revoked_at IS NOT NULL) THEN MAX(revoked_at) END,
       CASE WHEN bool_and(revoked_at IS NOT NULL) THEN 'LOGOUT' END
FROM refresh_tokens
GROUP BY family_id, user_id;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (family_id) REFERENCES login_sessions (id);
