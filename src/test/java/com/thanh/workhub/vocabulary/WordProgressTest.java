package com.thanh.workhub.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.thanh.workhub.vocabulary.internal.domain.ReviewResult;
import com.thanh.workhub.vocabulary.internal.domain.WordProgress;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

class WordProgressTest {
    private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

    @Test
    void firstRememberStartsLearningForOneDay() {
        var p = WordProgress.review(null, ReviewResult.REMEMBERED, T0);
        assertEquals(WordStatus.LEARNING, p.status());
        assertEquals(1, p.correctStreak());
        assertEquals(T0.plus(Duration.ofDays(1)), p.dueAt());
        assertEquals(T0, p.lastReviewedAt());
    }

    @Test
    void firstForgetRetriesInTenMinutes() {
        var p = WordProgress.review(null, ReviewResult.FORGOT, T0);
        assertEquals(WordStatus.LEARNING, p.status());
        assertEquals(0, p.correctStreak());
        assertEquals(T0.plus(Duration.ofMinutes(10)), p.dueAt());
    }

    @Test
    void threeRemembersOnDueDatesBecomeMastered() {
        var p = WordProgress.review(null, ReviewResult.REMEMBERED, T0);
        p = WordProgress.review(p, ReviewResult.REMEMBERED, p.dueAt());
        assertEquals(WordStatus.LEARNING, p.status());
        assertEquals(2, p.correctStreak());
        assertEquals(3, p.intervalDays());

        Instant third = p.dueAt();
        p = WordProgress.review(p, ReviewResult.REMEMBERED, third);
        assertEquals(WordStatus.MASTERED, p.status());
        assertEquals(3, p.correctStreak());
        assertEquals(third.plus(Duration.ofDays(14)), p.dueAt());
    }

    @Test
    void rememberBeforeDueOnlyRecordsReview() {
        var p = WordProgress.review(null, ReviewResult.REMEMBERED, T0);
        Instant later = T0.plus(Duration.ofHours(2));
        var again = WordProgress.review(p, ReviewResult.REMEMBERED, later);
        assertEquals(1, again.correctStreak());
        assertEquals(p.dueAt(), again.dueAt());
        assertEquals(later, again.lastReviewedAt());
    }

    @Test
    void masteredDoublesIntervalUpToSixtyDays() {
        var p = WordProgress.mastered(T0);
        assertEquals(14, p.intervalDays());
        p = WordProgress.review(p, ReviewResult.REMEMBERED, p.dueAt());
        assertEquals(28, p.intervalDays());
        p = WordProgress.review(p, ReviewResult.REMEMBERED, p.dueAt());
        assertEquals(56, p.intervalDays());
        p = WordProgress.review(p, ReviewResult.REMEMBERED, p.dueAt());
        assertEquals(60, p.intervalDays());
        assertEquals(WordStatus.MASTERED, p.status());
    }

    @Test
    void forgettingMasteredWordDropsToLearning() {
        var p = WordProgress.review(WordProgress.mastered(T0), ReviewResult.FORGOT, T0.plusSeconds(60));
        assertEquals(WordStatus.LEARNING, p.status());
        assertEquals(0, p.correctStreak());
        assertEquals(0, p.intervalDays());
    }
}
