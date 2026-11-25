-- ID thiết bị do app tự sinh (UUID, lưu trong Keychain/EncryptedSharedPreferences/localStorage).
-- Đăng nhập lại trên cùng máy thì thay phiên cũ thay vì chiếm thêm một chỗ trong giới hạn thiết bị.
ALTER TABLE login_sessions
    ADD COLUMN device_id VARCHAR(64);

ALTER TABLE login_sessions
    DROP CONSTRAINT ck_login_sessions_revoked_reason,
    ADD CONSTRAINT ck_login_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'REVOKED', 'EVICTED', 'TOKEN_REUSE', 'REPLACED'));

-- Mỗi máy của một user chỉ có một phiên chưa kết thúc.
CREATE UNIQUE INDEX uk_login_sessions_user_device_open
    ON login_sessions (user_id, device_id)
    WHERE revoked_at IS NULL AND device_id IS NOT NULL;
