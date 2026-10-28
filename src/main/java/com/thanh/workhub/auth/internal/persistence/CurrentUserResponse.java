package com.thanh.workhub.auth.internal.persistence;

public record CurrentUserResponse(
    Long id,
    String email
) {}