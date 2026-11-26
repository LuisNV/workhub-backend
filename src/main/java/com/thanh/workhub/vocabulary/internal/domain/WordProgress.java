package com.thanh.workhub.vocabulary.internal.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Tiến độ một từ (một dòng {@code user_vocabulary_progress}) và lịch ôn lặp lại ngắt quãng.
 *
 * <ul>
 *   <li>Quên: về LEARNING, streak 0, ôn lại sau {@link #RETRY_DELAY}.</li>
 *   <li>Nhớ khi đã đến hạn (hoặc lần đầu): streak + 1. LEARNING ôn lại sau 1 rồi 3 ngày; streak đạt
 *       {@link #MASTERED_STREAK} thì thành MASTERED, ôn lại sau 14 ngày; MASTERED nhớ tiếp thì nhân đôi
 *       khoảng ôn, tối đa {@link #MAX_INTERVAL_DAYS} ngày.</li>
 *   <li>Nhớ khi chưa đến hạn: chỉ ghi nhận đã ôn, không tăng streak (lật cùng một thẻ nhiều lần trong
 *       ngày không làm từ thành "Đã thuộc").</li>
 * </ul>
 */
public record WordProgress(
        WordStatus status,
        int correctStreak,
        int intervalDays,
        Instant dueAt,
        Instant lastReviewedAt) {

    public static final int MASTERED_STREAK = 3;
    public static final int MASTERED_INTERVAL_DAYS = 14;
    public static final int MAX_INTERVAL_DAYS = 60;
    public static final Duration RETRY_DELAY = Duration.ofMinutes(10);
    private static final int[] LEARNING_INTERVAL_DAYS = {1, 3}; // sau lần nhớ thứ 1, 2
    private static final int MAX_STREAK = 1000;

    /** Áp dụng một lần ôn. {@code current = null} nghĩa là từ chưa học. */
    public static WordProgress review(WordProgress current, ReviewResult result, Instant now) {
        if (result == ReviewResult.FORGOT) {
            return new WordProgress(WordStatus.LEARNING, 0, 0, now.plus(RETRY_DELAY), now);
        }
        if (current != null && now.isBefore(current.dueAt())) {
            return new WordProgress(current.status(), current.correctStreak(), current.intervalDays(),
                    current.dueAt(), now);
        }
        int streak = current == null ? 1 : Math.min(current.correctStreak() + 1, MAX_STREAK);
        if (current != null && current.status() == WordStatus.MASTERED) {
            return scheduled(WordStatus.MASTERED, streak,
                    Math.min(Math.max(current.intervalDays(), 1) * 2, MAX_INTERVAL_DAYS), now);
        }
        if (streak >= MASTERED_STREAK) return mastered(streak, now);
        return scheduled(WordStatus.LEARNING, streak, LEARNING_INTERVAL_DAYS[streak - 1], now);
    }

    /** Người dùng tự đánh dấu "Đã thuộc". */
    public static WordProgress mastered(Instant now) {
        return mastered(MASTERED_STREAK, now);
    }

    /** Người dùng tự đưa về "Đang học": cần ôn ngay. */
    public static WordProgress learning(Instant now) {
        return new WordProgress(WordStatus.LEARNING, 0, 0, now, now);
    }

    private static WordProgress mastered(int streak, Instant now) {
        return scheduled(WordStatus.MASTERED, streak, MASTERED_INTERVAL_DAYS, now);
    }

    private static WordProgress scheduled(WordStatus status, int streak, int intervalDays, Instant now) {
        return new WordProgress(status, streak, intervalDays, now.plus(Duration.ofDays(intervalDays)), now);
    }
}
