
package com.thanh.workhub.auth.internal.api.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn
) {}
