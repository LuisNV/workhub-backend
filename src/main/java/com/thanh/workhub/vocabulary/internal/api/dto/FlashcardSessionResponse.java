package com.thanh.workhub.vocabulary.internal.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Thẻ cho một lượt học: từ đến hạn ôn trước, sau đó từ mới.
 *
 * @param dueCount  tổng số từ đang đến hạn ôn của cấp (kể cả phần chưa lấy vào lượt này)
 * @param newCount  tổng số từ chưa học của cấp
 * @param nextDueAt thời điểm sớm nhất có từ đến hạn tiếp theo; null nếu không còn từ nào chờ ôn. Dùng cho màn
 *                  "Hết thẻ, quay lại lúc ..." khi {@code items} rỗng.
 */
public record FlashcardSessionResponse(
        int level,
        List<FlashcardResponse> items,
        long dueCount,
        long newCount,
        Instant nextDueAt) {
}
