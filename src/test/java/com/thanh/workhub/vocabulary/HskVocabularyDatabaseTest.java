package com.thanh.workhub.vocabulary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/** Màn Từ vựng HSK trên Postgres thật với từ điển seed (HSK 1: 300, HSK 2: 200, HSK 3: 500 từ). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class HskVocabularyDatabaseTest {
    private static final String LEVELS = "/api/v1/hsk/levels";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("delete from user_vocabulary_progress");
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0901234567\",\"password\":\"Admin@123456\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    void practiceDrawsTenDistinctHskRecordingsCoveringFourTones() throws Exception {
        var response = mvc.perform(get("/api/v1/tones/practice").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions.length()").value(10))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var questions = JsonPath.parse(response);
        java.util.List<String> paths = questions.read("$.data.questions[*].audioUrl");
        java.util.List<Integer> tones = questions.read("$.data.questions[*].correctTone");
        org.junit.jupiter.api.Assertions.assertEquals(10, paths.stream().distinct().count());
        org.junit.jupiter.api.Assertions.assertEquals(4, tones.stream().distinct().count());
        for (String path : paths) {
            org.junit.jupiter.api.Assertions.assertTrue(path.startsWith("/audio/hsk/"));
            org.junit.jupiter.api.Assertions.assertTrue(new org.springframework.core.io.ClassPathResource("static" + path).exists());
        }
    }

    @Test
    void listsAllNineLevelsWithProgress() throws Exception {
        progress(147, "MASTERED");   // 你好
        progress(4, "MASTERED");     // 爸爸
        progress(1, "LEARNING");     // 爱

        mvc.perform(get(LEVELS).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(9))
            .andExpect(jsonPath("$.data[0].level").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Sơ cấp"))
            .andExpect(jsonPath("$.data[0].available").value(true))
            .andExpect(jsonPath("$.data[0].totalWords").value(300))
            .andExpect(jsonPath("$.data[0].mastered").value(2))
            .andExpect(jsonPath("$.data[0].learning").value(1))
            .andExpect(jsonPath("$.data[0].notStarted").value(297))
            .andExpect(jsonPath("$.data[1].totalWords").value(200))
            .andExpect(jsonPath("$.data[2].totalWords").value(500))
            .andExpect(jsonPath("$.data[3].title").value("Trung cấp"))
            .andExpect(jsonPath("$.data[3].available").value(false))
            .andExpect(jsonPath("$.data[3].totalWords").value(0))
            .andExpect(jsonPath("$.data[8].title").value("Cao cấp"));
    }

    @Test
    void pagesWordsInSyllabusOrder() throws Exception {
        progress(4, "MASTERED");

        mvc.perform(get(LEVELS + "/1/words?size=5").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(5))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(1))
            .andExpect(jsonPath("$.data.items[0].hanzi").value("爱"))
            .andExpect(jsonPath("$.data.items[0].audioUrl").value("/audio/hsk/3.0-2025/1/1.m4a"))
            .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.data.items[3].hanzi").value("爸爸"))
            .andExpect(jsonPath("$.data.items[3].status").value("MASTERED"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(5))
            .andExpect(jsonPath("$.data.totalItems").value(300))
            .andExpect(jsonPath("$.data.totalPages").value(60));

        mvc.perform(get(LEVELS + "/1/words?size=5&page=1").header("Authorization", token))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(6));

        mvc.perform(get(LEVELS + "/1/words?page=99").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.totalItems").value(300));
    }

    @Test
    void filtersByStatus() throws Exception {
        progress(147, "MASTERED");
        progress(4, "MASTERED");
        progress(1, "LEARNING");

        mvc.perform(get(LEVELS + "/1/words?status=MASTERED").header("Authorization", token))
            .andExpect(jsonPath("$.data.totalItems").value(2))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(4))
            .andExpect(jsonPath("$.data.items[1].entryNumber").value(147));
        mvc.perform(get(LEVELS + "/1/words?status=LEARNING").header("Authorization", token))
            .andExpect(jsonPath("$.data.totalItems").value(1))
            .andExpect(jsonPath("$.data.items[0].hanzi").value("爱"));
        mvc.perform(get(LEVELS + "/1/words?status=NOT_STARTED").header("Authorization", token))
            .andExpect(jsonPath("$.data.totalItems").value(297))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(2));
        // Tiến độ HSK 1 không ảnh hưởng HSK 2.
        mvc.perform(get(LEVELS + "/2/words?status=NOT_STARTED").header("Authorization", token))
            .andExpect(jsonPath("$.data.totalItems").value(200));
    }

    @Test
    void levelWithoutWordsReturnsEmptyPage() throws Exception {
        mvc.perform(get(LEVELS + "/4/words").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.totalItems").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void returnsWordDetailWithExamples() throws Exception {
        progress(147, "LEARNING");

        mvc.perform(get(LEVELS + "/1/words/147").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hanzi").value("你好"))
            .andExpect(jsonPath("$.data.level").value(1))
            .andExpect(jsonPath("$.data.status").value("LEARNING"))
            .andExpect(jsonPath("$.data.hanViet").value("nhĩ hảo"))
            .andExpect(jsonPath("$.data.wordType").value("Cụm từ"))
            .andExpect(jsonPath("$.data.examples.length()").value(2))
            .andExpect(jsonPath("$.data.examples[0].sentenceZh").value("你好，我叫明安。"))
            .andExpect(jsonPath("$.data.examples[0].note").doesNotExist());

        mvc.perform(get(LEVELS + "/1/words/9999").header("Authorization", token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(1026));
        mvc.perform(get(LEVELS + "/4/words/1").header("Authorization", token))
            .andExpect(status().isNotFound());
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(get(LEVELS + "/0/words").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/10/words").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/abc/words").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/1/words?status=DONE").header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/1/words?page=-1").header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/1/words?size=0").header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/1/words?size=101").header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get(LEVELS + "/10/words/1").header("Authorization", token)).andExpect(status().isBadRequest());
    }

    @Test
    void requiresToken() throws Exception {
        mvc.perform(get(LEVELS)).andExpect(status().isUnauthorized());
        mvc.perform(get(LEVELS + "/1/words")).andExpect(status().isUnauthorized());
    }

    private void progress(int entryNumber, String status) {
        jdbc.update("""
                insert into user_vocabulary_progress
                    (user_id, syllabus, level, entry_number, status, due_at, last_reviewed_at)
                values (1, '3.0-2025', 1, ?, ?, now(), now())
                """, entryNumber, status);
    }
}
