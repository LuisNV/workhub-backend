package com.thanh.workhub.vocabulary.internal.api.dto;

import java.util.List;

import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/** Chi tiết một từ (bấm vào từ trong danh sách). Các trường giải thích có thể null. */
public record HskWordDetailResponse(
        int entryNumber,
        int level,
        String hanzi,
        String pinyin,
        String meaningVi,
        String audioUrl,
        WordStatus status,
        String hanViet,
        String hanVietNote,
        String wordType,
        String usageNote,
        List<Example> examples) {

    public record Example(String sentenceZh, String pinyin, String translationVi, String note) {
    }
}
