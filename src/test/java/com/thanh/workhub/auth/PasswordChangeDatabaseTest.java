package com.thanh.workhub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.thanh.workhub.platform.mail.EmailMessage;
import com.thanh.workhub.platform.mail.EmailSender;

/** Luồng đổi mật khẩu qua HTTP trên Postgres thật, dùng user seed (id 1, phone 0901234567). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class PasswordChangeDatabaseTest {
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private static final String OLD = "Admin@123456";
    private static final String NEW = "Matkhaumoi123";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean EmailSender mail;

    private final List<EmailMessage> sent = new ArrayList<>();

    record Tokens(String access, String refresh) {}

    @BeforeEach
    void reset() {
        sent.clear();
        doAnswer(call -> sent.add(call.getArgument(0))).when(mail).send(any());
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from login_sessions");
        jdbc.update("delete from password_change_requests");
        jdbc.update("delete from password_check_failures");
        jdbc.update("update app_users set password_hash = ? where id = 1", passwordEncoder.encode(OLD));
    }

    @Test
    void changesPasswordAndLogsOutOtherDevicesButKeepsThisOne() throws Exception {
        var me = login("me-device-0001", OLD);
        var other = login("other-device-01", OLD);

        String id = start(me, OLD, NEW, NEW, true)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("thanh.nguyen@example.com"))
            .andExpect(jsonPath("$.data.remainingAttempts").value(5))
            .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(id, "$.data.requestId");
        assertEquals("thanh.nguyen@example.com", sent.get(0).to());

        verify(me, requestId, code())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.loggedOutOtherDevices").value(true))
            .andExpect(jsonPath("$.data.revokedSessionCount").value(1));

        // Thiết bị này vẫn dùng được, thiết bị khác bị đăng xuất.
        refreshStatus(me.refresh()).andExpect(status().isOk());
        refreshStatus(other.refresh()).andExpect(status().isUnauthorized());
        // Mật khẩu cũ hết tác dụng, mật khẩu mới dùng được.
        loginStatus(OLD).andExpect(status().isUnauthorized());
        loginStatus(NEW).andExpect(status().isOk());
        // Có email báo mật khẩu đã đổi.
        assertEquals("Mật khẩu của bạn đã được thay đổi", sent.get(sent.size() - 1).subject());
    }

    @Test
    void keepsOtherDevicesWhenOptionIsOff() throws Exception {
        var me = login("me-device-0001", OLD);
        var other = login("other-device-01", OLD);
        String requestId = requestId(start(me, OLD, NEW, NEW, false));

        verify(me, requestId, code())
            .andExpect(jsonPath("$.data.loggedOutOtherDevices").value(false))
            .andExpect(jsonPath("$.data.revokedSessionCount").value(0));
        refreshStatus(other.refresh()).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordCountsDownThenLocks() throws Exception {
        var me = login("me-device-0001", OLD);
        for (int remaining = 4; remaining >= 1; remaining--) {
            start(me, "SaiMatKhau1", NEW, NEW, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng. Bạn còn " + remaining + " lần thử"));
        }
        start(me, "SaiMatKhau1", NEW, NEW, false)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(1018));
        // Đang khóa: kể cả mật khẩu đúng cũng bị chặn, không gửi mail.
        start(me, OLD, NEW, NEW, false)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(1018));
        assertTrue(sent.isEmpty());
        assertEquals(5, jdbc.queryForObject("select count(*) from password_check_failures", Integer.class));
    }

    @Test
    void emailChangeInProfileSharesTheSameAttemptLimit() throws Exception {
        var me = login("me-device-0001", OLD);
        // Sai 3 lần khi đổi email ở màn sửa hồ sơ.
        for (int remaining = 4; remaining >= 2; remaining--) {
            changeEmail(me, "SaiMatKhau1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng. Bạn còn " + remaining + " lần thử"));
        }
        // Sai tiếp ở màn đổi mật khẩu: cùng bộ đếm.
        start(me, "SaiMatKhau1", NEW, NEW, false)
            .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không đúng. Bạn còn 1 lần thử"));
        changeEmail(me, "SaiMatKhau1").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(1018));
        // Đang khóa: đổi email bằng mật khẩu đúng cũng bị chặn, email không đổi.
        changeEmail(me, OLD).andExpect(jsonPath("$.code").value(1018));
        assertEquals("thanh.nguyen@example.com",
                jdbc.queryForObject("select email from app_users where id = 1", String.class));
        // Sửa hồ sơ không đổi email thì không cần mật khẩu, vẫn làm được.
        mvc.perform(put("/api/v1/users/me").header("Authorization", bearer(me))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"fullName":"Tên mới","phone":"0901234567","address":"Hà Nội"}"""))
            .andExpect(status().isOk());
    }

    @Test
    void emailChangeWithCorrectPasswordWorks() throws Exception {
        var me = login("me-device-0001", OLD);
        changeEmail(me, OLD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("moi@example.com"))
            .andExpect(jsonPath("$.data.emailVerified").value(false));
        jdbc.update("update app_users set email = 'thanh.nguyen@example.com' where id = 1");
    }

    @Test
    void correctPasswordResetsFailureCounter() throws Exception {
        var me = login("me-device-0001", OLD);
        start(me, "SaiMatKhau1", NEW, NEW, false).andExpect(jsonPath("$.code").value(1002));
        start(me, OLD, NEW, NEW, false).andExpect(status().isCreated());
        assertEquals(0, jdbc.queryForObject("select count(*) from password_check_failures", Integer.class));
    }

    @Test
    void validatesNewPassword() throws Exception {
        var me = login("me-device-0001", OLD);
        start(me, OLD, NEW, "Matkhaukhac123", false).andExpect(jsonPath("$.code").value(1008));
        start(me, OLD, "yeu", "yeu", false).andExpect(jsonPath("$.code").value(1009));
        start(me, OLD, OLD, OLD, false).andExpect(jsonPath("$.code").value(1017));
        assertTrue(sent.isEmpty());
    }

    @Test
    void wrongCodeIsCountedAndCodeCanBeResentAfterCooldown() throws Exception {
        var me = login("me-device-0001", OLD);
        String requestId = requestId(start(me, OLD, NEW, NEW, false));
        String wrong = code().equals("000000") ? "111111" : "000000";

        verify(me, requestId, wrong)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1010))
            .andExpect(jsonPath("$.message").value("Mã xác thực không đúng. Bạn còn 4 lần thử"));
        assertEquals(1, jdbc.queryForObject(
                "select otp_attempts from password_change_requests where id = ?::uuid", Integer.class, requestId));
        // Mật khẩu chưa đổi khi chưa nhập đúng mã.
        loginStatus(OLD).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/password-changes/{id}/otp/resend", requestId).header("Authorization", bearer(me)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(1013));
        jdbc.update("update password_change_requests set otp_sent_at = otp_sent_at - interval '1 minute'");
        mvc.perform(post("/api/v1/auth/password-changes/{id}/otp/resend", requestId).header("Authorization", bearer(me)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.remainingAttempts").value(5));
        verify(me, requestId, code()).andExpect(status().isOk());
    }

    @Test
    void newRequestReplacesPendingOne() throws Exception {
        var me = login("me-device-0001", OLD);
        String first = requestId(start(me, OLD, NEW, NEW, false));
        String firstCode = code();
        requestId(start(me, OLD, "Khac12345678", "Khac12345678", false));

        verify(me, first, firstCode)
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value(1020));
    }

    @Test
    void passwordChangedElsewhereInvalidatesRequest() throws Exception {
        var me = login("me-device-0001", OLD);
        String requestId = requestId(start(me, OLD, NEW, NEW, false));
        jdbc.update("update app_users set password_hash = ? where id = 1", passwordEncoder.encode("Khac12345678"));

        verify(me, requestId, code()).andExpect(jsonPath("$.code").value(1020));
        loginStatus("Khac12345678").andExpect(status().isOk());
    }

    @Test
    void cannotUseAnotherUsersRequest() throws Exception {
        var me = login("me-device-0001", OLD);
        String requestId = requestId(start(me, OLD, NEW, NEW, false));
        // Chuyển yêu cầu sang user khác: user hiện tại không được thấy.
        jdbc.update("insert into app_users (full_name, email, phone, password_hash) "
                + "values ('Other', 'other-pw@example.com', '0977000000', 'x')");
        jdbc.update("update password_change_requests set user_id = "
                + "(select id from app_users where email = 'other-pw@example.com') where id = ?::uuid", requestId);

        verify(me, requestId, code()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1019));
        jdbc.update("delete from password_change_requests");
        jdbc.update("delete from app_users where email = 'other-pw@example.com'");
    }

    @Test
    void requiresToken() throws Exception {
        mvc.perform(post("/api/v1/auth/password-changes").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    // ---- Helpers ----

    private ResultActions start(Tokens tokens, String current, String next, String confirm, boolean logoutOthers)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/password-changes").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"currentPassword":"%s","newPassword":"%s","confirmPassword":"%s","logoutOtherDevices":%s}"""
                    .formatted(current, next, confirm, logoutOthers)));
    }

    private ResultActions changeEmail(Tokens tokens, String currentPassword) throws Exception {
        return mvc.perform(put("/api/v1/users/me").header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"fullName":"Nguyễn Văn Thành","phone":"0901234567","address":"Hà Nội",
                     "email":"moi@example.com","currentPassword":"%s"}""".formatted(currentPassword)));
    }

    private ResultActions verify(Tokens tokens, String requestId, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-changes/{id}/verify", requestId)
                .header("Authorization", bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private String requestId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.data.requestId");
    }

    private Tokens login(String deviceId, String password) throws Exception {
        String body = loginStatus(deviceId, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.data.accessToken"), JsonPath.read(body, "$.data.refreshToken"));
    }

    private ResultActions loginStatus(String password) throws Exception {
        return loginStatus(null, password);
    }

    private ResultActions loginStatus(String deviceId, String password) throws Exception {
        String idField = deviceId == null ? "" : "\"deviceId\":\"" + deviceId + "\",";
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{" + idField + "\"phone\":\"0901234567\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refreshStatus(String refreshToken) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private static String bearer(Tokens tokens) {
        return "Bearer " + tokens.access();
    }

    private String code() {
        for (int i = sent.size() - 1; i >= 0; i--) {
            var matcher = CODE.matcher(sent.get(i).textBody());
            if (matcher.find()) return matcher.group(1);
        }
        throw new AssertionError("Chưa có email chứa mã");
    }
}
