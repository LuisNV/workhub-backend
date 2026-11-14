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
import com.thanh.workhub.auth.internal.domain.RefreshToken;
import com.thanh.workhub.auth.internal.persistence.RefreshTokenRepository;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokenRepository;
    private final UserApi userApi;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
        RefreshTokenRepository tokenRepository,
        UserApi userApi,
        JwtService jwtService
    ) {
        this.tokenRepository = tokenRepository;
        this.userApi = userApi;
        this.jwtService = jwtService;
    }

    // Tạo Refresh Token lần đầu sau khi login
    @Transactional
    public String create(Long userId) {

        UUID familyId = UUID.randomUUID();

        return issueRefreshToken(userId, familyId);
    }

    // Làm mới Access Token và xoay vòng Refresh Token
    @Transactional
    public Optional<LoginResponse> refresh(String rawToken) {

        String hash = hashToken(rawToken);

        Optional<RefreshToken> optional =
            tokenRepository.findByTokenHashForUpdate(hash);

        if (optional.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken oldToken = optional.get();

        // Token bị thu hồi nhưng được sử dụng lại:
        // Thu hồi toàn bộ token thuộc cùng phiên
        if (oldToken.getRevokedAt() != null) {

            tokenRepository.revokeFamily(
                oldToken.getFamilyId(),
                Instant.now()
            );

            return Optional.empty();
        }

        // Token hết hạn
        if (!oldToken.isActive()) {
            return Optional.empty();
        }

        Optional<UserCredentials> userOptional =
            userApi.findCredentialsById(oldToken.getUserId());

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        UserCredentials user = userOptional.get();

        // Vô hiệu hóa Refresh Token cũ
        oldToken.revoke();
        tokenRepository.saveAndFlush(oldToken);

        // Phát Refresh Token mới cùng family
        String newRefreshToken = issueRefreshToken(
            user.id(),
            oldToken.getFamilyId()
        );

        // Tạo JWT mới
        String newAccessToken =
            jwtService.generateAccessToken(user.id(), user.email());

        return Optional.of(
            new LoginResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getExpiresInSeconds()
            )
        );
    }

    // Thu hồi phiên đăng nhập
    @Transactional
    public void logout(String rawToken) {

        String hash = hashToken(rawToken);

        tokenRepository.findByTokenHashForUpdate(hash)
            .ifPresent(token ->
                tokenRepository.revokeFamily(
                    token.getFamilyId(),
                    Instant.now()
                )
            );
    }

    // Tạo token ngẫu nhiên và chỉ lưu hash
    private String issueRefreshToken(
            Long userId,
            UUID familyId
    ) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes);

        RefreshToken entity = new RefreshToken(
            userId,
            familyId,
            hashToken(rawToken),
            Instant.now().plus(7, ChronoUnit.DAYS)
        );

        tokenRepository.save(entity);

        return rawToken;
    }

    // SHA-256 hash, không lưu refresh token gốc
    private String hashToken(String value) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(bytes);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                "SHA-256 unavailable", ex
            );
        }
    }
}
