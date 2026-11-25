package com.thanh.workhub.auth.internal.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.auth.internal.api.dto.ChangeRegistrationEmailRequest;
import com.thanh.workhub.auth.internal.api.dto.CompleteRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.CompleteRegistrationResponse;
import com.thanh.workhub.auth.internal.api.dto.InitiateRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.RegistrationResponse;
import com.thanh.workhub.auth.internal.api.dto.StartRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.VerifyRegistrationRequest;
import com.thanh.workhub.auth.internal.application.RegistrationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Đăng ký tài khoản mới. Không cần JWT; {@code registrationId} đóng vai trò khóa của phiên. */
@RestController
@RequestMapping("/api/v1/auth/registrations")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** Bước 1 – Tạo phiên đăng ký. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse start(@Valid @RequestBody StartRegistrationRequest request) {
        return registrationService.start(request);
    }

    /** Xem trạng thái phiên (mở lại app giữa chừng). */
    @GetMapping("/{id}")
    public RegistrationResponse get(@PathVariable UUID id) {
        return registrationService.get(id);
    }

    /** Bước 2 – Khởi tạo: đặt mật khẩu và gửi mã xác thực về email. */
    @PostMapping("/{id}/initiate")
    public RegistrationResponse initiate(@PathVariable UUID id,
            @Valid @RequestBody InitiateRegistrationRequest request) {
        return registrationService.initiate(id, request);
    }

    /** Bước 3 – Gửi lại mã. */
    @PostMapping("/{id}/otp/resend")
    public RegistrationResponse resendOtp(@PathVariable UUID id) {
        return registrationService.resendOtp(id);
    }

    /** Bước 3 – Đổi địa chỉ email (gửi mã tới email mới). */
    @PutMapping("/{id}/email")
    public RegistrationResponse changeEmail(@PathVariable UUID id,
            @Valid @RequestBody ChangeRegistrationEmailRequest request) {
        return registrationService.changeEmail(id, request);
    }

    /** Bước 3 – Xác thực mã. */
    @PostMapping("/{id}/verify")
    public RegistrationResponse verify(@PathVariable UUID id,
            @Valid @RequestBody VerifyRegistrationRequest request) {
        return registrationService.verify(id, request);
    }

    /** Bước 4 – Hoàn tất: tạo tài khoản và đăng nhập luôn. */
    @PostMapping("/{id}/complete")
    public CompleteRegistrationResponse complete(@PathVariable UUID id,
            @Valid @RequestBody(required = false) CompleteRegistrationRequest request, HttpServletRequest http) {
        var body = request == null ? new CompleteRegistrationRequest(null, null, null) : request;
        return registrationService.complete(id,
                DeviceInfoResolver.from(http, body.deviceId(), body.deviceName(), body.platform()));
    }
}
