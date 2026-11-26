package com.thanh.workhub.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/** Thẻ cho một lượt học trên Postgres thật, dùng user seed (id 1). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class HskFlashcardDatabaseTest {

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
    void newUserGetsNewWordsInSyllabusOrder() throws Exception {
        flashcards(1, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.level").value(1))
            .andExpect(jsonPath("$.data.items.length()").value(20))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(1))
            .andExpect(jsonPath("$.data.items[0].hanzi").value("爱"))
            .andExpect(jsonPath("$.data.items[0].status").value("NOT_STARTED"))
            .andExpect(jsonPath("$.data.items[0].reason").value("NEW"))
            .andExpect(jsonPath("$.data.items[0].audioUrl").value("/audio/hsk/3.0-2025/1/1.m4a"))
            .andExpect(jsonPath("$.data.items[19].entryNumber").value(20))
            .andExpect(jsonPath("$.data.dueCount").value(0))
            .andExpect(jsonPath("$.data.newCount").value(300))
            .andExpect(jsonPath("$.data.nextDueAt").doesNotExist());
    }

    @Test
    void dueWordsComeFirstMostOverdueFirst() throws Exception {
        progress(147, "LEARNING", "now() - interval '1 hour'");
        progress(4, "MASTERED", "now() - interval '2 days'");
        progress(1, "LEARNING", "now() + interval '1 day'");   // chưa đến hạn: không lấy

        flashcards(1, "?size=5")
            .andExpect(jsonPath("$.data.items.length()").value(5))
            .andExpect(jsonPath("$.data.items[0].entryNumber").value(4))
            .andExpect(jsonPath("$.data.items[0].reason").value("DUE"))
            .andExpect(jsonPath("$.data.items[0].status").value("MASTERED"))
            .andExpect(jsonPath("$.data.items[1].entryNumber").value(147))
            .andExpect(jsonPath("$.data.items[1].reason").value("DUE"))
            .andExpect(jsonPath("$.data.items[1].hanViet").value("nhĩ hảo"))
            .andExpect(jsonPath("$.data.items[1].example.sentenceZh").value("你好，我叫明安。"))
            // Từ mới bỏ qua các từ đã có tiến độ (1, 4).
            .andExpect(jsonPath("$.data.items[2].entryNumber").value(2))
            .andExpect(jsonPath("$.data.items[2].reason").value("NEW"))
            .andExpect(jsonPath("$.data.items[3].entryNumber").value(3))
            .andExpect(jsonPath("$.data.items[4].entryNumber").value(5))
            .andExpect(jsonPath("$.data.dueCount").value(2))
            .andExpect(jsonPath("$.data.newCount").value(297));
    }

    @Test
    void dueWordsFillWholeSessionWhenEnough() throws Exception {
        for (int entry = 10; entry < 15; entry++) progress(entry, "LEARNING", "now() - interval '1 minute'");

        flashcards(1, "?size=3")
            .andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].reason").value("DUE"))
            .andExpect(jsonPath("$.data.items[2].reason").value("DUE"))
            .andExpect(jsonPath("$.data.dueCount").value(5));
    }

    @Test
    void emptyWhenEverythingLearnedAndNotDue() throws Exception {
        jdbc.update("""
                insert into user_vocabulary_progress
                    (user_id, syllabus, level, entry_number, status, due_at, last_reviewed_at)
                select 1, syllabus, level, entry_number, 'MASTERED', now() + interval '3 days', now()
                from hsk_vocabulary where level = 2
                """);
        jdbc.update("update user_vocabulary_progress set status = 'LEARNING', due_at = now() + interval '1 hour'"
                + " where entry_number = 301");

        String body = flashcards(2, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.dueCount").value(0))
            .andExpect(jsonPath("$.data.newCount").value(0))
            .andExpect(jsonPath("$.data.nextDueAt").exists())
            .andReturn().getResponse().getContentAsString();
        String expected = jdbc.queryForObject(
                "select min(due_at) from user_vocabulary_progress where level = 2", java.sql.Timestamp.class)
                .toInstant().toString();
        assertEquals(expected, JsonPath.read(body, "$.data.nextDueAt"));
    }

    @Test
    void levelWithoutWordsReturnsEmpty() throws Exception {
        flashcards(4, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.newCount").value(0));
    }

    @Test
    void doesNotCreateProgress() throws Exception {
        flashcards(1, "").andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_vocabulary_progress", Integer.class));
    }

    @Test
    void validatesInput() throws Exception {
        flashcards(0, "").andExpect(status().isBadRequest());
        flashcards(10, "").andExpect(status().isBadRequest());
        flashcards(1, "?size=0").andExpect(status().isBadRequest());
        flashcards(1, "?size=51").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hsk/levels/1/flashcards")).andExpect(status().isUnauthorized());
    }

    private ResultActions flashcards(int level, String query) throws Exception {
        return mvc.perform(get("/api/v1/hsk/levels/" + level + "/flashcards" + query).header("Authorization", token));
    }

    private void progress(int entryNumber, String status, String dueAtSql) {
        jdbc.update("insert into user_vocabulary_progress"
                + " (user_id, syllabus, level, entry_number, status, due_at, last_reviewed_at)"
                + " select 1, syllabus, level, entry_number, ?, " + dueAtSql + ", now()"
                + " from hsk_vocabulary where entry_number = ?", status, entryNumber);
    }
}
