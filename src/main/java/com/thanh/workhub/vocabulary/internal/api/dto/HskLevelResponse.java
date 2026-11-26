package com.thanh.workhub.vocabulary.internal.api.dto;

/**
 * Một tab cấp HSK kèm thẻ tiến độ. {@code available = false} khi cấp đó chưa có từ nào (hiện khóa).
 */
public record HskLevelResponse(
        int level,
        String title,
        boolean available,
        int totalWords,
        int mastered,
        int learning,
        int notStarted) {
}
