package com.thanh.workhub.auth.internal.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.auth.internal.api.dto.PasswordChangeResponse;
import com.thanh.workhub.auth.internal.api.dto.PasswordChangedResponse;
import com.thanh.workhub.auth.internal.api.dto.StartPasswordChangeRequest;
import com.thanh.workhub.auth.internal.api.dto.VerifyOtpRequest;
import com.thanh.workhub.auth.internal.application.PasswordChangeService;
import com.thanh.workhub.platform.security.CurrentUserId;

import jakarta.validation.Valid;

/** Đổi mật khẩu khi đang đăng nhập (cần access token). */
@RestController
@RequestMapping("/api/v1/auth/password-changes")
public class PasswordChangeController {
    private final PasswordChangeService passwordChangeService;

    public PasswordChangeController(PasswordChangeService passwordChangeService) {
        this.passwordChangeService = passwordChangeService;
    }

    /** Bước 1 – Kiểm tra mật khẩu hiện tại và mật khẩu mới, gửi mã xác minh về email. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PasswordChangeResponse start(@CurrentUserId Long userId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartPasswordChangeRequest request) {
        return passwordChangeService.start(userId, CurrentSession.id(jwt), request);
    }

    /** Gửi lại mã. */
    @PostMapping("/{id}/otp/resend")
    public PasswordChangeResponse resendOtp(@CurrentUserId Long userId, @PathVariable UUID id) {
        return passwordChangeService.resendOtp(userId, id);
    }

    /** Bước 2 – Nhập mã: xác nhận đổi mật khẩu. */
    @PostMapping("/{id}/verify")
    public PasswordChangedResponse verify(@CurrentUserId Long userId, @PathVariable UUID id,
            @Valid @RequestBody VerifyOtpRequest request) {
        return passwordChangeService.verify(userId, id, request);
    }
}
