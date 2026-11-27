package com.thanh.workhub.vocabulary.internal.api.dto;

import java.util.List;

import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/** Một dòng trong danh sách từ. {@code audioUrl} là đường dẫn tương đối, null nếu chưa có audio. */
public record HskWordResponse(
        int entryNumber,
        String hanzi,
        String pinyin,
        String meaningVi,
        String audioUrl,
        WordStatus status) {

    public List<CharacterDecomposition> getDecomposition() {
        return CharacterDecomposition.forWord(hanzi);
    }
}
