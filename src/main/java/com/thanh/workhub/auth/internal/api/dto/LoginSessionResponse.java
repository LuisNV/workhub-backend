package com.thanh.workhub.auth.internal.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Một thiết bị đang đăng nhập. {@code current = true} là thiết bị đang gọi API. */
public record LoginSessionResponse(
        UUID id,
        String deviceName,
        String platform,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastActiveAt,
        boolean current) {
}
