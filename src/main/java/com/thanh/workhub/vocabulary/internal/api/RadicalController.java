package com.thanh.workhub.vocabulary.internal.api;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

@RestController
public class RadicalController {
    public record Example(String hanzi, String hanViet, String meaningVi) {}
    public record Radical(int number, String symbol, String canonical, List<String> forms, String hanViet,
            String aliasVi, String meaningVi, int strokes, int canonicalStrokes, boolean common,
            String descriptionVi, List<Example> examples) {}
    public record Result(int total, List<Radical> items) {}

    private final List<Radical> radicals;

    public RadicalController(ObjectMapper mapper) throws IOException {
        try (var in = new ClassPathResource("vocabulary/radicals.json").getInputStream()) {
            radicals = mapper.readValue(in, new TypeReference<List<Radical>>() {});
        }
    }

    @GetMapping("/api/v1/radicals")
    public Result radicals(@RequestParam(defaultValue = "false") boolean commonOnly,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "all") String strokes) {
        if (!List.of("all", "2", "3", "4", "5+").contains(strokes)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "strokes phải là all, 2, 3, 4 hoặc 5+");
        }
        String query = normalize(q.trim());
        var items = radicals.stream().filter(r -> !commonOnly || r.common())
                .filter(r -> strokes.equals("all") || strokes.equals(String.valueOf(r.strokes()))
                        || (strokes.equals("5+") && r.strokes() >= 5))
                .filter(r -> query.isEmpty() || r.forms().contains(query) || query.equals(String.valueOf(r.number()))
                        || normalize(r.hanViet()).contains(query) || normalize(r.meaningVi()).contains(query)
                        || (r.aliasVi() != null && normalize(r.aliasVi()).contains(query)))
                .toList();
        return new Result(items.size(), items);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
    }
}
