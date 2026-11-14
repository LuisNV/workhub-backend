package com.thanh.workhub.auth.internal.application;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

@Service
public class AuthService {
    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserApi userApi, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();
        boolean loginByEmail = identifier.contains("@");

        UserCredentials user = (loginByEmail
                ? userApi.findCredentialsByEmail(identifier.toLowerCase(Locale.ROOT))
                : userApi.findCredentialsByPhone(identifier))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (loginByEmail && !user.emailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String accessToken = jwtService.generateAccessToken(user.id(), user.email());
        String refreshToken = refreshTokenService.create(user.id());
        return new LoginResponse(accessToken, refreshToken, "Bearer", jwtService.getExpiresInSeconds());
    }
}
