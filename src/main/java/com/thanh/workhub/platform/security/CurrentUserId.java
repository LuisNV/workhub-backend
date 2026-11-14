package com.thanh.workhub.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject ID của user đang đăng nhập (lấy từ subject của JWT) vào tham số controller.
 *
 * <pre>{@code
 * @PutMapping("/me")
 * public UserProfileResponse update(@CurrentUserId Long userId, ...) { ... }
 * }</pre>
 *
 * Trả 401 nếu không có JWT hoặc subject không phải số nguyên dương.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
