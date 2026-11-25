package com.thanh.workhub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

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

/** Thiết bị đăng nhập trên Postgres thật, dùng user seed (id 1, phone 0901234567). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class LoginSessionDatabaseTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    record Tokens(String access, String refresh) {}

    @BeforeEach
    void clean() {
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from login_sessions");
    }

    @Test
    void recordsDeviceAndMarksCurrentSession() throws Exception {
        var phone = login("iPhone 15 Pro", "iOS 18.1");
        login("MacBook", "macOS");

        mvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + phone.access()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[?(@.deviceName == 'iPhone 15 Pro')].current").value(true))
            .andExpect(jsonPath("$.data[?(@.deviceName == 'iPhone 15 Pro')].platform").value("iOS 18.1"))
            .andExpect(jsonPath("$.data[?(@.deviceName == 'iPhone 15 Pro')].userAgent").value("TingTing/1.0"))
            .andExpect(jsonPath("$.data[?(@.deviceName == 'MacBook')].current").value(false));
    }

    @Test
    void sixthLoginLogsOutLeastRecentlyUsedDevice() throws Exception {
        List<Tokens> devices = new ArrayList<>();
        for (int i = 1; i <= 5; i++) devices.add(login("Device " + i, "iOS"));
        // Device k hoạt động lần cuối cách đây (6 - k) giờ: Device 1 cũ nhất.
        for (int i = 1; i <= 5; i++) {
            jdbc.update("update login_sessions set last_active_at = now() - make_interval(hours => ?) "
                    + "where device_name = ?", 6 - i, "Device " + i);
        }
        // Device 1 vừa refresh nên thành mới nhất; Device 2 giờ là cũ nhất.
        devices.set(0, refresh(devices.get(0).refresh()));

        var sixth = login("Device 6", "Android");

        assertEquals(List.of("Device 1", "Device 3", "Device 4", "Device 5", "Device 6"), activeDevices());
        assertEquals("EVICTED", jdbc.queryForObject(
                "select revoked_reason from login_sessions where device_name = 'Device 2'", String.class));
        // Thiết bị bị đẩy ra không refresh được nữa, thiết bị còn lại vẫn dùng bình thường.
        refreshFails(devices.get(1).refresh());
        refresh(devices.get(2).refresh());
        mvc.perform(get("/api/v1/auth/sessions").header("Authorization", "Bearer " + sixth.access()))
            .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void concurrentLoginsNeverExceedLimit() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Tokens>> logins = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int n = i;
                logins.add(() -> login("Parallel " + n, "iOS"));
            }
            for (var future : pool.invokeAll(logins)) future.get();
        } finally {
            pool.shutdown();
        }
        assertEquals(5, activeDevices().size());
    }

    @Test
    void revokeOneDeviceAndOthers() throws Exception {
        var me = login("Me", "iOS");
        var other1 = login("Other 1", "Android");
        login("Other 2", "Web");

        String other1Id = jdbc.queryForObject(
                "select id::text from login_sessions where device_name = 'Other 1'", String.class);
        mvc.perform(delete("/api/v1/auth/sessions/{id}", other1Id).header("Authorization", "Bearer " + me.access()))
            .andExpect(status().isOk());
        refreshFails(other1.refresh());
        assertEquals(List.of("Me", "Other 2"), activeDevices());

        // Xóa lần nữa: không còn hoạt động thì 404.
        mvc.perform(delete("/api/v1/auth/sessions/{id}", other1Id).header("Authorization", "Bearer " + me.access()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(1015));

        mvc.perform(post("/api/v1/auth/sessions/revoke-others").header("Authorization", "Bearer " + me.access()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revokedCount").value(1));
        assertEquals(List.of("Me"), activeDevices());
    }

    @Test
    void cannotRevokeSessionOfAnotherUser() throws Exception {
        var me = login("Me", "iOS");
        jdbc.update("insert into app_users (full_name, email, phone, address, password_hash) "
                + "values ('Other', 'other-user@example.com', '0988888888', null, 'x')");
        jdbc.update("insert into login_sessions (id, user_id, created_at, last_active_at, expires_at) "
                + "select gen_random_uuid(), id, now(), now(), now() + interval '7 days' from app_users "
                + "where email = 'other-user@example.com'");
        String foreignId = jdbc.queryForObject("select s.id::text from login_sessions s join app_users u "
                + "on u.id = s.user_id where u.email = 'other-user@example.com'", String.class);

        mvc.perform(delete("/api/v1/auth/sessions/{id}", foreignId).header("Authorization", "Bearer " + me.access()))
            .andExpect(status().isNotFound());
        jdbc.update("delete from login_sessions where id = ?::uuid", foreignId);
        jdbc.update("delete from app_users where email = 'other-user@example.com'");
    }

    @Test
    void logoutEndsSessionAndRefreshKeepsSameSession() throws Exception {
        var device = login("Phone", "iOS");
        var refreshed = refresh(device.refresh());
        assertEquals(List.of("Phone"), activeDevices());

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshed.refresh() + "\"}"))
            .andExpect(status().isOk());
        assertEquals(List.of(), activeDevices());
        assertEquals("LOGOUT", jdbc.queryForObject(
                "select revoked_reason from login_sessions where device_name = 'Phone'", String.class));
    }

    @Test
    void sameDeviceLoggingInAgainReplacesItsOldSession() throws Exception {
        var first = login("phone-uuid-0001", "iPhone 15 Pro", "iOS 18.1");
        // Người dùng đổi tên máy rồi đăng nhập lại (ví dụ cài lại app): vẫn là một máy.
        var second = login("phone-uuid-0001", "iPhone của Anh", "iOS 18.2");

        assertEquals(List.of("iPhone của Anh"), activeDevices());
        assertEquals("REPLACED", jdbc.queryForObject(
                "select revoked_reason from login_sessions where device_name = 'iPhone 15 Pro'", String.class));
        refreshFails(first.refresh());
        refresh(second.refresh());
    }

    @Test
    void sameDeviceReloginDoesNotEvictOtherDevicesAtLimit() throws Exception {
        for (int i = 1; i <= 5; i++) login("device-id-000" + i, "Device " + i, "iOS");
        login("device-id-0003", "Device 3", "iOS");
        assertEquals(List.of("Device 1", "Device 2", "Device 3", "Device 4", "Device 5"), activeDevices());
    }

    @Test
    void rejectsMalformedDeviceId() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"phone":"0901234567","password":"Admin@123456","deviceId":"bad id with spaces"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sessionsRequireToken() throws Exception {
        mvc.perform(get("/api/v1/auth/sessions")).andExpect(status().isUnauthorized());
    }

    // ---- Helpers ----

    private Tokens login(String deviceName, String platform) throws Exception {
        return login(null, deviceName, platform);
    }

    private Tokens login(String deviceId, String deviceName, String platform) throws Exception {
        String idField = deviceId == null ? "" : "\"deviceId\":\"" + deviceId + "\",";
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "TingTing/1.0")
                .content("""
                    {%s"phone":"0901234567","password":"Admin@123456","deviceName":"%s","platform":"%s"}"""
                    .formatted(idField, deviceName, platform)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.data.accessToken"), JsonPath.read(body, "$.data.refreshToken"));
    }

    private Tokens refresh(String refreshToken) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.data.accessToken"), JsonPath.read(body, "$.data.refreshToken"));
    }

    private void refreshFails(String refreshToken) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    private List<String> activeDevices() {
        return jdbc.queryForList("select device_name from login_sessions "
                + "where revoked_at is null and expires_at > now() order by device_name", String.class);
    }
}
