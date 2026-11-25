package com.thanh.workhub.auth.internal.application;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.auth.internal.domain.DeviceInfo;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

@Service
public class AuthService {
    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserApi userApi, PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, DeviceInfo device) {
        String identifier = request.identifier().trim();
        boolean loginByEmail = identifier.contains("@");

        UserCredentials user = (loginByEmail
                ? userApi.findCredentialsByEmail(identifier.toLowerCase(Locale.ROOT))
                : userApi.findCredentialsByPhone(identifier.replaceAll("\\s+", "")))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (loginByEmail && !user.emailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return refreshTokenService.create(user.id(), user.email(), device);
    }
}
