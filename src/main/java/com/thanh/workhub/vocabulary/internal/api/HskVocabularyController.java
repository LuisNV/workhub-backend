package com.thanh.workhub.vocabulary.internal.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.platform.security.CurrentUserId;
import com.thanh.workhub.vocabulary.internal.api.dto.DailyWordResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.FlashcardSessionResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskLevelResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordDetailResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordPageResponse;
import com.thanh.workhub.vocabulary.internal.application.HskVocabularyService;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/** Màn "Từ vựng HSK" (cần access token: trạng thái học là của từng người). */
@RestController
@RequestMapping("/api/v1/hsk")
public class HskVocabularyController {
    private final HskVocabularyService vocabularyService;

    public HskVocabularyController(HskVocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @GetMapping("/word-of-the-day")
    public DailyWordResponse dailyWord() {
        return vocabularyService.dailyWord();
    }

    /** Tab HSK 1–9 và thẻ tiến độ của từng cấp. */
    @GetMapping("/levels")
    public List<HskLevelResponse> levels(@CurrentUserId Long userId) {
        return vocabularyService.levels(userId);
    }

    /** Danh sách từ của một cấp; bỏ trống {@code status} là "Tất cả". */
    @GetMapping("/levels/{level}/words")
    public HskWordPageResponse words(@CurrentUserId Long userId, @PathVariable int level,
            @RequestParam(required = false) WordStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return vocabularyService.words(userId, level, status, page, size);
    }

    /** Thẻ cho một lượt học: từ đến hạn ôn trước, sau đó từ mới. */
    @GetMapping("/levels/{level}/flashcards")
    public FlashcardSessionResponse flashcards(@CurrentUserId Long userId, @PathVariable int level,
            @RequestParam(defaultValue = "20") int size) {
        return vocabularyService.flashcards(userId, level, size);
    }

    /** Chi tiết một từ: Hán Việt, ghi chú, ví dụ. */
    @GetMapping("/levels/{level}/words/{entryNumber}")
    public HskWordDetailResponse word(@CurrentUserId Long userId, @PathVariable int level,
            @PathVariable int entryNumber) {
        return vocabularyService.word(userId, level, entryNumber);
    }
}
