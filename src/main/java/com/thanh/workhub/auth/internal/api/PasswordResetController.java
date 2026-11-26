package com.thanh.workhub.auth.internal.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.auth.internal.api.dto.CompletePasswordResetRequest;
import com.thanh.workhub.auth.internal.api.dto.PasswordResetCompletedResponse;
import com.thanh.workhub.auth.internal.api.dto.PasswordResetResponse;
import com.thanh.workhub.auth.internal.api.dto.PasswordResetVerifiedResponse;
import com.thanh.workhub.auth.internal.api.dto.StartPasswordResetRequest;
import com.thanh.workhub.auth.internal.api.dto.VerifyOtpRequest;
import com.thanh.workhub.auth.internal.application.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Quên mật khẩu. Không cần JWT; {@code resetId} là khóa của yêu cầu. */
@RestController
@RequestMapping("/api/v1/auth/password-resets")
public class PasswordResetController {
    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** Màn 1 – Nhập email hoặc số điện thoại, gửi mã. Luôn 201 dù tài khoản có tồn tại hay không. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PasswordResetResponse start(@Valid @RequestBody StartPasswordResetRequest request,
            HttpServletRequest http) {
        return passwordResetService.start(request.identifier(), http.getRemoteAddr());
    }

    /** Màn 2 – Gửi lại mã. */
    @PostMapping("/{id}/otp/resend")
    public PasswordResetResponse resendOtp(@PathVariable UUID id) {
        return passwordResetService.resendOtp(id);
    }

    /** Màn 2 – Nhập mã, nhận resetToken. */
    @PostMapping("/{id}/verify")
    public PasswordResetVerifiedResponse verify(@PathVariable UUID id, @Valid @RequestBody VerifyOtpRequest request) {
        return passwordResetService.verify(id, request);
    }

    /** Màn 3 – Đặt mật khẩu mới; mọi thiết bị bị đăng xuất (màn 4). */
    @PostMapping("/{id}/complete")
    public PasswordResetCompletedResponse complete(@PathVariable UUID id,
            @Valid @RequestBody CompletePasswordResetRequest request) {
        return passwordResetService.complete(id, request);
    }
}
