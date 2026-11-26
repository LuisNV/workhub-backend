package com.thanh.workhub.user;

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

/** Cài đặt học trên Postgres thật, dùng user seed (id 1). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class LearningSettingsDatabaseTest {
    private static final String URL = "/api/v1/users/me/learning-settings";
    private static final String BODY = """
        {
          "dailyGoalMinutes": 15,
          "reminder": {
            "enabled": true,
            "time": "20:00",
            "days": ["SAT", "MON", "TUE", "WED", "THU", "FRI", "MON"],
            "skipIfGoalMet": true,
            "timezone": "Asia/Ho_Chi_Minh"
          },
          "script": "SIMPLIFIED",
          "pinyinMode": "ALWAYS"
        }""";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("delete from user_learning_settings");
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0901234567\",\"password\":\"Admin@123456\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    void returnsDefaultsBeforeFirstSave() throws Exception {
        mvc.perform(get(URL).header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dailyGoalMinutes").value(15))
            .andExpect(jsonPath("$.data.reminder.enabled").value(false))
            .andExpect(jsonPath("$.data.reminder.time").value("20:00"))
            .andExpect(jsonPath("$.data.reminder.days.length()").value(7))
            .andExpect(jsonPath("$.data.script").value("SIMPLIFIED"))
            .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    void savesAndReadsBack() throws Exception {
        save(BODY).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reminder.days.length()").value(6))
            .andExpect(jsonPath("$.data.reminder.days[0]").value("MON"))
            .andExpect(jsonPath("$.data.reminder.days[5]").value("SAT"))
            .andExpect(jsonPath("$.data.updatedAt").exists());

        save(BODY.replace("\"dailyGoalMinutes\": 15", "\"dailyGoalMinutes\": 30")
                .replace("SIMPLIFIED", "TRADITIONAL").replace("\"ALWAYS\"", "\"ON_TAP\"")
                .replace("\"enabled\": true", "\"enabled\": false").replace("\"20:00\"", "\"07:30\""))
            .andExpect(status().isOk());

        mvc.perform(get(URL).header("Authorization", token))
            .andExpect(jsonPath("$.data.dailyGoalMinutes").value(30))
            .andExpect(jsonPath("$.data.reminder.enabled").value(false))
            .andExpect(jsonPath("$.data.reminder.time").value("07:30"))
            .andExpect(jsonPath("$.data.reminder.timezone").value("Asia/Ho_Chi_Minh"))
            .andExpect(jsonPath("$.data.script").value("TRADITIONAL"))
            .andExpect(jsonPath("$.data.pinyinMode").value("ON_TAP"));
    }

    @Test
    void validatesInput() throws Exception {
        save(BODY.replace("\"dailyGoalMinutes\": 15", "\"dailyGoalMinutes\": 0")).andExpect(status().isBadRequest());
        save(BODY.replace("\"20:00\"", "\"25:00\"")).andExpect(status().isBadRequest());
        save(BODY.replace("\"SAT\"", "\"XYZ\"")).andExpect(status().isBadRequest());
        save(BODY.replace("SIMPLIFIED", "HANZI")).andExpect(status().isBadRequest());
        save(BODY.replace("\"ALWAYS\"", "\"SOMETIMES\"")).andExpect(status().isBadRequest());
        save(BODY.replace("Asia/Ho_Chi_Minh", "Mars/Base"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Múi giờ không hợp lệ, ví dụ: Asia/Ho_Chi_Minh"));
        save(BODY.replace("[\"SAT\", \"MON\", \"TUE\", \"WED\", \"THU\", \"FRI\", \"MON\"]", "[]"))
            .andExpect(jsonPath("$.message").value("Vui lòng chọn ít nhất 1 ngày nhắc học"));
        save("{\"dailyGoalMinutes\": 15}").andExpect(status().isBadRequest());
    }

    @Test
    void requiresToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put(URL).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
