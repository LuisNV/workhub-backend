package com.thanh.workhub.vocabulary.internal.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.vocabulary.internal.api.dto.TonePracticeQuestion;
import com.thanh.workhub.vocabulary.internal.persistence.HskVocabularyQueries;

@RestController
@RequestMapping("/api/v1/tones")
public class ToneController {
    public record Tone(int number, String pinyin, List<Integer> pitch, String descriptionVi, String audioUrl) {}
    public record Guide(List<Tone> tones, String sandhiTipVi) {}
    public record Practice(List<TonePracticeQuestion> questions) {}

    private static final Guide GUIDE = new Guide(List.of(
            new Tone(1, "mā", List.of(5, 5), "Cao và đều, giữ giọng ngang.", "/audio/pinyin/5988.wav"),
            new Tone(2, "má", List.of(3, 5), "Đi từ vừa lên cao, gần giống dấu sắc.", "/audio/pinyin/9ebb.wav"),
            new Tone(3, "mǎ", List.of(2, 1, 4), "Khi đọc riêng, giọng xuống thấp rồi đi lên.",
                    "/audio/hsk/3.0-2025/3/764.m4a"),
            new Tone(4, "mà", List.of(5, 1), "Bắt đầu cao rồi hạ nhanh, dứt khoát.", "/audio/pinyin/9a82.wav")),
            "Khi hai thanh 3 đứng liền nhau, thanh đầu đọc thành thanh 2. Ví dụ, nǐ hǎo đọc gần như ní hǎo.");

    private final HskVocabularyQueries queries;
    private final String syllabus;

    public ToneController(HskVocabularyQueries queries, @Value("${app.hsk.syllabus}") String syllabus) {
        this.queries = queries;
        this.syllabus = syllabus;
    }

    @GetMapping
    public Guide guide() {
        return GUIDE;
    }

    @GetMapping("/practice")
    public Practice practice() {
        return new Practice(queries.findTonePractice(syllabus));
    }
}
