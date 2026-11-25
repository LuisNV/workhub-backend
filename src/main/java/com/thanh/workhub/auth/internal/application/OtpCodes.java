package com.thanh.workhub.auth.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/** Sinh, băm và so khớp mã OTP 6 số. Chỉ lưu hash, không lưu mã gốc. */
final class OtpCodes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpCodes() {
    }

    static String generate() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    /** Gắn ID của yêu cầu vào hash để cùng một mã ở hai yêu cầu khác nhau cho ra hash khác nhau. */
    static String hash(UUID ownerId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((ownerId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** So sánh thời gian hằng số. */
    static boolean matches(String expectedHash, UUID ownerId, String code) {
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                hash(ownerId, code).getBytes(StandardCharsets.US_ASCII));
    }
}
