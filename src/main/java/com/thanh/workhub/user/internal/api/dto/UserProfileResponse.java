package com.thanh.workhub.user.internal.api.dto;

import java.time.LocalDate;

public record UserProfileResponse(Long id, String fullName, String email, String phone, String address,
        String gender, LocalDate dateOfBirth, String avatarUrl, boolean emailVerified) {}
