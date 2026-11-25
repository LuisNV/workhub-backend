package com.thanh.workhub.auth.internal.application;

import java.nio.charset.StandardCharsets;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

/** Quy tắc mật khẩu dùng chung cho đăng ký và đổi mật khẩu. */
final class PasswordPolicy {
    private PasswordPolicy() {
    }

    /** Ít nhất 8 ký tự, có chữ hoa, chữ thường và ít nhất 1 chữ số; nhập lại phải khớp. */
    static void validate(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) throw new ApiException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        boolean strong = password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit);
        if (!strong) throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK);
        // BCrypt chỉ dùng 72 byte đầu; chữ có dấu chiếm nhiều byte nên kiểm tra theo byte.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK, "Mật khẩu quá dài");
        }
    }
}
