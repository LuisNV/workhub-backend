package com.thanh.workhub.vocabulary.internal.api.dto;

import java.util.List;

import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/**
 * Một thẻ ghi nhớ. Mặt trước: {@code hanzi}; mặt sau: phiên âm, nghĩa, Hán Việt, ví dụ.
 * {@code hanViet}, {@code wordType}, {@code example} có thể null.
 */
public record FlashcardResponse(
        int entryNumber,
        String hanzi,
        String pinyin,
        String meaningVi,
        String audioUrl,
        WordStatus status,
        Reason reason,
        String hanViet,
        String wordType,
        HskWordDetailResponse.Example example) {

    public List<CharacterDecomposition> getDecomposition() {
        return CharacterDecomposition.forWord(hanzi);
    }


    /** DUE: đến hạn ôn; NEW: từ chưa học. */
    public enum Reason {
        DUE,
        NEW
    }
}
