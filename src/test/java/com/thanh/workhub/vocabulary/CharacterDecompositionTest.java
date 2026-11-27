package com.thanh.workhub.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.workhub.vocabulary.internal.api.dto.DailyWordResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.FlashcardResponse;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

class CharacterDecompositionTest {
    @Test
    void dailyWordIncludesDecompositionInJson() {
        var word = new DailyWordResponse(LocalDate.of(2026, 11, 27), 3, 785, "牛", "niú", "bò",
                "ngưu", "Danh từ", "/audio/hsk/3.0-2025/3/785.m4a");
        var rows = new ObjectMapper().findAndRegisterModules().valueToTree(word).get("decomposition");
        assertEquals(1, rows.size());
        assertEquals("ngưu", rows.get(0).get("hanViet").asText());
        assertEquals("con bò", rows.get(0).get("components").get(0).get("meaningVi").asText());
        assertEquals("Hai sừng nhô ra từ đầu con bò.", rows.get(0).get("mnemonicVi").asText());
    }

    @Test
    void flashcardProvidesSeparateDisplayFieldsForEachCharacter() {
        var card = new FlashcardResponse(147, "你好", "nǐ hǎo", "xin chào", null,
                WordStatus.NOT_STARTED, FlashcardResponse.Reason.NEW, null, null, null);
        var rows = new ObjectMapper().valueToTree(card).get("decomposition");

        assertEquals(2, rows.size());
        assertEquals("你", rows.get(0).get("character").asText());
        assertEquals("nhĩ", rows.get(0).get("hanViet").asText());
        var parts = rows.get(0).get("components");
        assertEquals("亻", parts.get(0).get("character").asText());
        assertEquals("nhân", parts.get(0).get("hanViet").asText());
        assertEquals("người", parts.get(0).get("meaningVi").asText());
        assertEquals("nhĩ", parts.get(1).get("hanViet").asText());
        assertEquals("hảo", rows.get(1).get("hanViet").asText());
        assertEquals("Trái/phải", rows.get(0).get("layoutVi").asText());
        assertEquals("亻 (người) + 尔 (ngươi)", rows.get(0).get("componentsVi").asText());
        assertEquals("Người mà mình gọi là ngươi → bạn.", rows.get(0).get("mnemonicVi").asText());
        assertEquals("好", rows.get(1).get("character").asText());
        assertEquals("女 (phụ nữ) + 子 (con)", rows.get(1).get("componentsVi").asText());
        assertEquals("Mẹ bế con → điều tốt, hay.", rows.get(1).get("mnemonicVi").asText());
    }
}
