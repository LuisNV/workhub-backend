package com.thanh.workhub.auth.internal.domain;

/** Lý do một phiên đăng nhập bị kết thúc. */
public enum SessionRevokeReason {
    /** Người dùng tự đăng xuất trên chính thiết bị đó. */
    LOGOUT,
    /** Bị đăng xuất từ thiết bị khác (quản lý thiết bị). */
    REVOKED,
    /** Bị đẩy ra vì đăng nhập thiết bị mới khi đã đủ số thiết bị tối đa. */
    EVICTED,
    /** Refresh token đã thu hồi bị dùng lại: nghi bị đánh cắp, khóa cả phiên. */
    TOKEN_REUSE,
    /** Cùng máy (cùng deviceId) đăng nhập lại: phiên cũ được thay bằng phiên mới. */
    REPLACED
}
