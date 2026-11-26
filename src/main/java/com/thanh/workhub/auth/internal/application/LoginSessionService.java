package com.thanh.workhub.auth.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.api.dto.LoginSessionResponse;
import com.thanh.workhub.auth.internal.domain.DeviceInfo;
import com.thanh.workhub.auth.internal.domain.LoginSession;
import com.thanh.workhub.auth.internal.domain.SessionRevokeReason;
import com.thanh.workhub.auth.internal.persistence.LoginSessionRepository;
import com.thanh.workhub.auth.internal.persistence.RefreshTokenRepository;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

/**
 * Quản lý thiết bị đăng nhập. Mỗi user có tối đa {@code app.auth.max-sessions-per-user} phiên còn hiệu lực;
 * đăng nhập thêm thì phiên lâu không dùng nhất bị đăng xuất. Cùng một máy ({@code deviceId}) đăng nhập lại
 * thì thay phiên cũ của máy đó, không chiếm thêm chỗ.
 */
@Service
public class LoginSessionService {
    private final LoginSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final Clock clock;
    private final int maxSessionsPerUser;

    public LoginSessionService(LoginSessionRepository sessions, RefreshTokenRepository tokens, Clock clock,
            @Value("${app.auth.max-sessions-per-user}") int maxSessionsPerUser) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    /** Mở phiên mới cho thiết bị; đẩy phiên cũ nhất ra nếu đã đủ số thiết bị. Gọi trong transaction. */
    @Transactional
    public LoginSession open(Long userId, DeviceInfo device, Instant expiresAt) {
        Instant now = clock.instant();
        sessions.lockUser(userId);
        if (device.deviceId() != null) {
            for (var sameDevice : sessions.findByUserIdAndDeviceIdAndRevokedAtIsNull(userId, device.deviceId())) {
                revoke(sameDevice, SessionRevokeReason.REPLACED, now);
            }
        }
        List<LoginSession> active = sessions.findActiveByUserId(userId, now); // mới dùng nhất đứng đầu
        // Dùng vòng lặp vì dữ liệu cũ (trước khi có giới hạn) có thể đã vượt quá số thiết bị cho phép.
        for (int i = active.size() - 1; i >= maxSessionsPerUser - 1; i--) {
            revoke(active.get(i), SessionRevokeReason.EVICTED, now);
        }
        return sessions.save(new LoginSession(userId, device, now, expiresAt));
    }

    @Transactional(readOnly = true)
    public List<LoginSessionResponse> list(Long userId, UUID currentSessionId) {
        return sessions.findActiveByUserId(userId, clock.instant()).stream()
                .map(session -> toResponse(session, currentSessionId))
                .toList();
    }

    /** Đăng xuất một thiết bị của chính mình (kể cả thiết bị hiện tại). */
    @Transactional
    public void revoke(Long userId, UUID sessionId) {
        Instant now = clock.instant();
        var session = sessions.findByIdAndUserId(sessionId, userId)
                .filter(found -> found.isActive(now))
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        revoke(session, SessionRevokeReason.REVOKED, now);
    }

    /** Đăng xuất mọi thiết bị khác, giữ lại thiết bị đang gọi. Trả về số thiết bị bị đăng xuất. */
    @Transactional
    public int revokeOthers(Long userId, UUID currentSessionId) {
        if (currentSessionId == null) throw new ApiException(ErrorCode.SESSION_UNKNOWN);
        Instant now = clock.instant();
        int count = 0;
        for (var session : sessions.findActiveByUserId(userId, now)) {
            if (!session.getId().equals(currentSessionId)) {
                revoke(session, SessionRevokeReason.REVOKED, now);
                count++;
            }
        }
        return count;
    }

    /** Đăng xuất mọi thiết bị của user (đặt lại mật khẩu). Trả về số thiết bị bị đăng xuất. */
    @Transactional
    public int revokeAll(Long userId, SessionRevokeReason reason) {
        Instant now = clock.instant();
        // Khóa như khi đăng nhập để không có phiên mới chen vào giữa lúc đang đăng xuất.
        sessions.lockUser(userId);
        var active = sessions.findActiveByUserId(userId, now);
        active.forEach(session -> revoke(session, reason, now));
        return active.size();
    }

    /** Kết thúc phiên và thu hồi mọi refresh token của nó. */
    void revoke(LoginSession session, SessionRevokeReason reason, Instant now) {
        session.revoke(reason, now);
        tokens.revokeFamily(session.getId(), now);
    }

    private static LoginSessionResponse toResponse(LoginSession session, UUID currentSessionId) {
        return new LoginSessionResponse(session.getId(), session.getDeviceName(), session.getPlatform(),
                session.getUserAgent(), session.getIpAddress(), session.getCreatedAt(),
                session.getLastActiveAt(), session.getId().equals(currentSessionId));
    }
}
