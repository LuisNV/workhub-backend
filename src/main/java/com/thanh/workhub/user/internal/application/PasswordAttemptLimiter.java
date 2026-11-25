package com.thanh.workhub.user.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.internal.domain.PasswordCheckFailure;
import com.thanh.workhub.user.internal.persistence.PasswordCheckFailureRepository;

/**
 * Giới hạn số lần nhập sai mật khẩu hiện tại (dùng chung cho đổi mật khẩu và đổi email): sai {@code max} lần
 * trong {@code lockout} phút thì khóa tới khi lần sai cũ ra khỏi cửa sổ. Chặn dò mật khẩu khi access token bị lộ.
 */
@Component
public class PasswordAttemptLimiter {
    private final PasswordCheckFailureRepository failures;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockout;

    public PasswordAttemptLimiter(PasswordCheckFailureRepository failures, Clock clock,
            @Value("${app.password-change.max-current-password-attempts}") int maxAttempts,
            @Value("${app.password-change.lockout-minutes}") long lockoutMinutes) {
        this.failures = failures;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    /** Ném PASSWORD_CHECK_LOCKED nếu đang bị khóa. */
    @Transactional(readOnly = true)
    public void ensureNotLocked(Long userId) {
        Instant now = clock.instant();
        var recent = failures.findByUserIdAndFailedAtAfterOrderByFailedAtAsc(userId, now.minus(lockout));
        if (recent.size() >= maxAttempts) {
            // Mở khóa khi lần sai thứ (size - max + 1) tính từ cũ nhất ra khỏi cửa sổ.
            Instant unlockAt = recent.get(recent.size() - maxAttempts).getFailedAt().plus(lockout);
            throw locked(Duration.between(now, unlockAt));
        }
    }

    /**
     * Ghi một lần sai trong transaction riêng (vẫn được lưu dù request ném lỗi và rollback), rồi trả lỗi
     * kèm số lần thử còn lại để nơi gọi ném ra.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApiException recordFailure(Long userId) {
        Instant now = clock.instant();
        failures.save(new PasswordCheckFailure(userId, now));
        int remaining = maxAttempts - failures.findByUserIdAndFailedAtAfterOrderByFailedAtAsc(
                userId, now.minus(lockout)).size();
        if (remaining <= 0) return locked(lockout);
        return new ApiException(ErrorCode.CURRENT_PASSWORD_INCORRECT,
                "Mật khẩu hiện tại không đúng. Bạn còn " + remaining + " lần thử");
    }

    @Transactional
    public void reset(Long userId) {
        failures.deleteByUser(userId);
    }

    /** Mỗi giờ xóa các lần sai cũ hơn 1 ngày (đã quá cửa sổ khóa từ lâu). */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeOld() {
        failures.deleteFailedBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private static ApiException locked(Duration wait) {
        long minutes = Math.max(1, (wait.toSeconds() + 59) / 60); // làm tròn lên
        return new ApiException(ErrorCode.PASSWORD_CHECK_LOCKED,
                "Nhập sai mật khẩu quá nhiều lần, vui lòng thử lại sau " + minutes + " phút");
    }
}
