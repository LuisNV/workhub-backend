package com.thanh.workhub.auth.internal.api;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.thanh.workhub.platform.security.JwtService;

/** Đọc ID phiên đăng nhập (thiết bị) từ claim {@code sid} của access token. */
final class CurrentSession {
    private CurrentSession() {
    }

    /** Null nếu token phát trước khi có claim sid hoặc sid không hợp lệ. */
    static UUID id(Jwt jwt) {
        String sid = jwt == null ? null : jwt.getClaimAsString(JwtService.SESSION_ID_CLAIM);
        if (sid == null) return null;
        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
