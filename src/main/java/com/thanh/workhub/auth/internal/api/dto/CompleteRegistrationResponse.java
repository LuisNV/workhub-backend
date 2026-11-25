package com.thanh.workhub.auth.internal.api.dto;

import com.thanh.workhub.user.UserProfileResponse;

/** Bước 4: user vừa tạo và token đăng nhập luôn, client vào thẳng app ("Bắt đầu học"). */
public record CompleteRegistrationResponse(
        UserProfileResponse user,
        boolean biometricEnabled,
        LoginResponse tokens) {
}
