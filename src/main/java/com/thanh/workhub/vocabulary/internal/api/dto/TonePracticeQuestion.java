package com.thanh.workhub.vocabulary.internal.api.dto;

public record TonePracticeQuestion(String syllable, String audioUrl, int correctTone, int syllablePosition,
            int level, int entryNumber, String hanzi) {}
