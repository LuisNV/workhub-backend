package com.thanh.workhub.vocabulary;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.workhub.platform.web.ApiResponseAdvice;
import com.thanh.workhub.vocabulary.internal.api.PinyinChartController;

class PinyinChartTest {
    @Test
    void servesAllTabsWithPlayableExamplesAndContractedSpellings() throws Exception {
        var controller = new PinyinChartController(new ObjectMapper());
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiResponseAdvice()).build()
                .perform(get("/api/v1/pinyin/chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initials[0].items[0].symbol").value("b"))
                .andExpect(jsonPath("$.data.finals[0].items[5].symbol").value("ü"))
                .andExpect(jsonPath("$.data.combinations").isArray());

        var chart = controller.chart();
        assertTrue(chart.combinations().contains(new PinyinChartController.Combination("j", "ü", "ju")));
        assertTrue(chart.combinations().contains(new PinyinChartController.Combination("", "iou", "you")));
        Stream.concat(chart.initials().stream(), chart.finals().stream()).flatMap(group -> group.items().stream())
                .forEach(sound -> assertTrue(new ClassPathResource("static" + sound.audioUrl()).exists(), sound.symbol()));
    }
}
