
package com.thanh.workhub.auth.internal.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.auth.internal.api.dto.RefreshTokenRequest;
import com.thanh.workhub.auth.internal.application.AuthService;
import com.thanh.workhub.auth.internal.application.RefreshTokenService;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, UserService userService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    // Đăng nhập không cần JWT
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
            authService.login(request)
        );
    }
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return refreshTokenService
            .refresh(request.refreshToken())
            .map(ResponseEntity::ok)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh Token không hợp lệ hoặc đã hết hạn"
                )
            );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        refreshTokenService.logout(request.refreshToken());

        return ResponseEntity.noContent().build();
    }
    // Kiểm tra người dùng đang đăng nhập
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var user = authService.getProfile(jwt.getClaimAsString("email"));

        return ResponseEntity.ok(
            new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAddress()
            )
        );
    }
}
