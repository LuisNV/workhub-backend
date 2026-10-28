package com.thanh.workhub.auth.internal.api.dto;

public record CurrentUserResponse(
    Long id,
    String email,
    String phone,
    String address,
    String fullName
) {}