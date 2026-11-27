package com.thanh.workhub.vocabulary.internal.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyWordResponse(LocalDate date, int level, int entryNumber, String hanzi, String pinyin,
        String meaningVi, String hanViet, String wordType, String audioUrl) {

    public List<CharacterDecomposition> getDecomposition() {
        return CharacterDecomposition.forWord(hanzi);
    }
}
