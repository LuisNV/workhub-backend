
package com.thanh.workhub.auth.internal.persistence;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}
