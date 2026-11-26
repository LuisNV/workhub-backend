package com.thanh.workhub.vocabulary.internal.api.dto;

import java.time.Instant;

import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/** Tiến độ một từ sau khi ghi. Từ NOT_STARTED có {@code dueAt}, {@code lastReviewedAt} = null. */
public record WordProgressResponse(
        int level,
        int entryNumber,
        WordStatus status,
        int correctStreak,
        Instant dueAt,
        Instant lastReviewedAt) {
}
