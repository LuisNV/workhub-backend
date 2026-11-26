package com.thanh.workhub.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/** Ghi tiến độ học từ HSK trên Postgres thật, dùng user seed (id 1). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class HskProgressDatabaseTest {
    private static final String REVIEWS = "/api/v1/hsk/reviews";

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
    void reviewCreatesProgressAndReturnsLevelCounts() throws Exception {
        review("""
            {"items":[
              {"level":1,"entryNumber":147,"result":"REMEMBERED"},
              {"level":1,"entryNumber":4,"result":"FORGOT"},
              {"level":2,"entryNumber":301,"result":"REMEMBERED"}
            ]}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(147))
            .andExpect(jsonPath("$.data.items[0].status").value("LEARNING"))
            .andExpect(jsonPath("$.data.items[0].correctStreak").value(1))
            .andExpect(jsonPath("$.data.items[0].dueAt").exists())
            .andExpect(jsonPath("$.data.items[1].correctStreak").value(0))
            .andExpect(jsonPath("$.data.levels.length()").value(2))
            .andExpect(jsonPath("$.data.levels[0].level").value(1))
            .andExpect(jsonPath("$.data.levels[0].learning").value(2))
            .andExpect(jsonPath("$.data.levels[0].notStarted").value(298))
            .andExpect(jsonPath("$.data.levels[1].level").value(2))
            .andExpect(jsonPath("$.data.levels[1].learning").value(1));

        mvc.perform(get("/api/v1/hsk/levels/1/words?status=LEARNING").header("Authorization", token))
            .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    @Test
    void sameWordTwiceInOneSessionCountsOnce() throws Exception {
        review("""
            {"items":[
              {"level":1,"entryNumber":147,"result":"REMEMBERED"},
              {"level":1,"entryNumber":147,"result":"REMEMBERED"},
              {"level":1,"entryNumber":147,"result":"REMEMBERED"}
            ]}""")
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].status").value("LEARNING"))
            .andExpect(jsonPath("$.data.items[0].correctStreak").value(1));
    }

    @Test
    void becomesMasteredAfterThreeDueReviews() throws Exception {
        String remember = "{\"items\":[{\"level\":1,\"entryNumber\":147,\"result\":\"REMEMBERED\"}]}";
        review(remember);
        makeDue(147);
        review(remember).andExpect(jsonPath("$.data.items[0].correctStreak").value(2));
        makeDue(147);
        review(remember)
            .andExpect(jsonPath("$.data.items[0].status").value("MASTERED"))
            .andExpect(jsonPath("$.data.levels[0].mastered").value(1))
            .andExpect(jsonPath("$.data.levels[0].learning").value(0));

        review("{\"items\":[{\"level\":1,\"entryNumber\":147,\"result\":\"FORGOT\"}]}")
            .andExpect(jsonPath("$.data.items[0].status").value("LEARNING"))
            .andExpect(jsonPath("$.data.items[0].correctStreak").value(0));
    }

    @Test
    void unknownWordRejectsWholeSession() throws Exception {
        review("""
            {"items":[
              {"level":1,"entryNumber":147,"result":"REMEMBERED"},
              {"level":4,"entryNumber":1,"result":"REMEMBERED"}
            ]}""")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(1026))
            .andExpect(jsonPath("$.message").value("Không tìm thấy từ số 1 của HSK 4"));
        assertEquals(0, count());
    }

    @Test
    void validatesReviewBody() throws Exception {
        review("{\"items\":[]}").andExpect(status().isBadRequest());
        review("{}").andExpect(status().isBadRequest());
        review("{\"items\":[{\"level\":0,\"entryNumber\":1,\"result\":\"REMEMBERED\"}]}")
            .andExpect(status().isBadRequest());
        review("{\"items\":[{\"level\":1,\"entryNumber\":0,\"result\":\"REMEMBERED\"}]}")
            .andExpect(status().isBadRequest());
        review("{\"items\":[{\"level\":1,\"entryNumber\":1}]}").andExpect(status().isBadRequest());
        review("{\"items\":[{\"level\":1,\"entryNumber\":1,\"result\":\"MAYBE\"}]}")
            .andExpect(status().isBadRequest());
        String tooMany = "{\"items\":[" + "{\"level\":1,\"entryNumber\":1,\"result\":\"FORGOT\"},".repeat(100)
                + "{\"level\":1,\"entryNumber\":2,\"result\":\"FORGOT\"}]}";
        review(tooMany).andExpect(status().isBadRequest());
        assertEquals(0, count());
    }

    @Test
    void marksWordManually() throws Exception {
        mark(1, 147, "MASTERED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.word.status").value("MASTERED"))
            .andExpect(jsonPath("$.data.word.correctStreak").value(3))
            .andExpect(jsonPath("$.data.level.level").value(1))
            .andExpect(jsonPath("$.data.level.mastered").value(1));

        mark(1, 147, "LEARNING")
            .andExpect(jsonPath("$.data.word.status").value("LEARNING"))
            .andExpect(jsonPath("$.data.word.correctStreak").value(0))
            .andExpect(jsonPath("$.data.level.learning").value(1));

        mark(1, 147, "NOT_STARTED")
            .andExpect(jsonPath("$.data.word.status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.data.word.dueAt").doesNotExist())
            .andExpect(jsonPath("$.data.level.notStarted").value(300));
        assertEquals(0, count());

        // Đặt lại từ chưa học: không lỗi.
        mark(1, 147, "NOT_STARTED").andExpect(status().isOk());
    }

    @Test
    void markingSameStatusKeepsSchedule() throws Exception {
        mark(1, 147, "MASTERED");
        String dueAt = jdbc.queryForObject("select due_at::text from user_vocabulary_progress", String.class);
        mark(1, 147, "MASTERED").andExpect(status().isOk());
        assertEquals(dueAt, jdbc.queryForObject("select due_at::text from user_vocabulary_progress", String.class));
    }

    @Test
    void validatesManualMark() throws Exception {
        mark(1, 9999, "MASTERED").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1026));
        mark(10, 1, "MASTERED").andExpect(status().isBadRequest());
        mark(1, 1, "DONE").andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/hsk/levels/1/words/1/progress").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void requiresToken() throws Exception {
        mvc.perform(post(REVIEWS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"level\":1,\"entryNumber\":1,\"result\":\"FORGOT\"}]}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/hsk/levels/1/words/1/progress").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"MASTERED\"}"))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions review(String body) throws Exception {
        return mvc.perform(post(REVIEWS).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions mark(int level, int entryNumber, String status) throws Exception {
        return mvc.perform(put("/api/v1/hsk/levels/" + level + "/words/" + entryNumber + "/progress")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    /** Giả lập đã đến hạn ôn. */
    private void makeDue(int entryNumber) {
        jdbc.update("update user_vocabulary_progress set due_at = now() - interval '1 minute' where entry_number = ?",
                entryNumber);
    }

    private int count() {
        Integer n = jdbc.queryForObject("select count(*) from user_vocabulary_progress", Integer.class);
        return n == null ? 0 : n;
    }
}
