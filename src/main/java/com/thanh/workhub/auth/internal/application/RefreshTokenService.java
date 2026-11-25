package com.thanh.workhub.auth.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.auth.internal.domain.DeviceInfo;
import com.thanh.workhub.auth.internal.domain.RefreshToken;
import com.thanh.workhub.auth.internal.domain.SessionRevokeReason;
import com.thanh.workhub.auth.internal.persistence.LoginSessionRepository;
import com.thanh.workhub.auth.internal.persistence.RefreshTokenRepository;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

/**
 * Phát và xoay vòng token. Mỗi lần đăng nhập mở một phiên ({@link LoginSessionService}); mọi refresh token
 * của phiên có {@code familyId} = ID phiên, access token mang claim {@code sid} = ID phiên.
 */
@Service
public class RefreshTokenService {
    private static final int REFRESH_TOKEN_DAYS = 7;

    private final RefreshTokenRepository tokenRepository;
    private final LoginSessionRepository sessionRepository;
    private final LoginSessionService loginSessions;
    private final UserApi userApi;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokenRepository, LoginSessionRepository sessionRepository,
            LoginSessionService loginSessions, UserApi userApi, JwtService jwtService) {
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.loginSessions = loginSessions;
        this.userApi = userApi;
        this.jwtService = jwtService;
    }

    /** Đăng nhập thành công trên một thiết bị: mở phiên mới và phát cặp token đầu tiên. */
    @Transactional
    public LoginResponse create(Long userId, String email, DeviceInfo device) {
        Instant expiresAt = refreshExpiry();
        var session = loginSessions.open(userId, device, expiresAt);
        String refreshToken = issueRefreshToken(userId, session.getId(), expiresAt);
        return tokens(userId, email, session.getId(), refreshToken);
    }

    /** Làm mới Access Token và xoay vòng Refresh Token. */
    @Transactional
    public Optional<LoginResponse> refresh(String rawToken, DeviceInfo device) {
        Optional<RefreshToken> optional = tokenRepository.findByTokenHashForUpdate(hashToken(rawToken));
        if (optional.isEmpty()) return Optional.empty();
        RefreshToken oldToken = optional.get();
        Instant now = Instant.now();
        var session = sessionRepository.findById(oldToken.getFamilyId()).orElse(null);

        // Token đã thu hồi bị dùng lại: có thể bị đánh cắp, khóa cả phiên.
        // (Phiên bị đăng xuất từ xa cũng đi vào đây vì token của nó đã bị thu hồi; revoke() không ghi đè lý do cũ.)
        if (oldToken.getRevokedAt() != null) {
            if (session != null) loginSessions.revoke(session, SessionRevokeReason.TOKEN_REUSE, now);
            else tokenRepository.revokeFamily(oldToken.getFamilyId(), now);
            return Optional.empty();
        }
        if (!oldToken.isActive() || session == null || !session.isActive(now)) return Optional.empty();

        Optional<UserCredentials> user = userApi.findCredentialsById(oldToken.getUserId());
        if (user.isEmpty()) return Optional.empty();

        oldToken.revoke();
        tokenRepository.saveAndFlush(oldToken);
        Instant expiresAt = refreshExpiry();
        session.touch(device, now, expiresAt);
        String newRefreshToken = issueRefreshToken(user.get().id(), session.getId(), expiresAt);
        return Optional.of(tokens(user.get().id(), user.get().email(), session.getId(), newRefreshToken));
    }

    /** Đăng xuất thiết bị đang dùng refresh token này. */
    @Transactional
    public void logout(String rawToken) {
        tokenRepository.findByTokenHashForUpdate(hashToken(rawToken)).ifPresent(token -> {
            Instant now = Instant.now();
            sessionRepository.findById(token.getFamilyId()).ifPresentOrElse(
                    session -> loginSessions.revoke(session, SessionRevokeReason.LOGOUT, now),
                    () -> tokenRepository.revokeFamily(token.getFamilyId(), now));
        });
    }

    private LoginResponse tokens(Long userId, String email, UUID sessionId, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(userId, email, sessionId);
        return new LoginResponse(accessToken, refreshToken, "Bearer", jwtService.getExpiresInSeconds());
    }

    private static Instant refreshExpiry() {
        return Instant.now().plus(REFRESH_TOKEN_DAYS, ChronoUnit.DAYS);
    }

    // Tạo token ngẫu nhiên và chỉ lưu hash
    private String issueRefreshToken(Long userId, UUID familyId, Instant expiresAt) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        tokenRepository.save(new RefreshToken(userId, familyId, hashToken(rawToken), expiresAt));
        return rawToken;
    }

    // SHA-256 hash, không lưu refresh token gốc
    private String hashToken(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
