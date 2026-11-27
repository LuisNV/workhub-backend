package com.thanh.workhub.vocabulary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thanh.workhub.platform.web.ApiResponseAdvice;
import com.thanh.workhub.vocabulary.internal.api.RadicalController;
import com.thanh.workhub.vocabulary.internal.api.ToneController;

class LearningToolsTest {
    @Test
    void servesBothRadicalTabsAndFindsWaterByDisplayedStrokeCount() throws Exception {
        var controller = new RadicalController(new ObjectMapper());
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiResponseAdvice()).build();
        mvc.perform(get("/api/v1/radicals")).andExpect(jsonPath("$.data.total").value(214));
        mvc.perform(get("/api/v1/radicals").param("commonOnly", "true")).andExpect(jsonPath("$.data.total").value(16));
        for (String query : new String[]{"85", "水", "氵", "thuy", "nuoc"}) {
            mvc.perform(get("/api/v1/radicals").param("q", query).param("strokes", "3"))
                    .andExpect(jsonPath("$.data.items[0].number").value(85))
                    .andExpect(jsonPath("$.data.items[0].examples[0].hanzi").value("河"));
        }
    }

    @Test
    void guideShowsFourTones() throws Exception {
        var controller = new ToneController(org.mockito.Mockito.mock(
                com.thanh.workhub.vocabulary.internal.persistence.HskVocabularyQueries.class), "3.0-2025");
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiResponseAdvice()).build();
        mvc.perform(get("/api/v1/tones")).andExpect(jsonPath("$.data.tones.length()").value(4));
    }
}
