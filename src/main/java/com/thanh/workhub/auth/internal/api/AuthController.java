package com.thanh.workhub.auth.internal.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.auth.internal.api.dto.RefreshTokenRequest;
import com.thanh.workhub.auth.internal.application.AuthService;
import com.thanh.workhub.auth.internal.application.RefreshTokenService;
import com.thanh.workhub.platform.security.CurrentUserId;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserProfileResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserApi userApi;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, UserApi userApi) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userApi = userApi;
    }

    // Đăng nhập không cần JWT
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.refresh(request.refreshToken())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    // Hồ sơ của người dùng đang đăng nhập (đọc theo ID trong token, không theo email)
    @GetMapping("/me")
    public UserProfileResponse me(@CurrentUserId Long userId) {
        return userApi.getProfile(userId);
    }
}
