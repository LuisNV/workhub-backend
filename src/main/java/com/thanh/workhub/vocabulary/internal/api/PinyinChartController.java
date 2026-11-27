package com.thanh.workhub.vocabulary.internal.api;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class PinyinChartController {
    public record Sound(String symbol, String pinyin, String hanzi, String meaningVi, String tipVi, String audioUrl) {}
    public record Group(String titleVi, List<Sound> items) {}
    public record Combination(String initial, String finalSymbol, String syllable) {}
    public record Chart(List<Group> initials, List<Group> finals, List<Combination> combinations) {}

    private final Chart chart;

    public PinyinChartController(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("vocabulary/pinyin-chart.json").getInputStream()) {
            chart = mapper.readValue(input, Chart.class);
        }
    }

    @GetMapping("/api/v1/pinyin/chart")
    public Chart chart() {
        return chart;
    }
}
