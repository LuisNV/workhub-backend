package com.thanh.workhub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
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

/** Luồng quên mật khẩu qua HTTP trên Postgres thật, dùng user seed (id 1, phone 0901234567). */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class PasswordResetDatabaseTest {
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private static final String EMAIL = "thanh.nguyen@example.com";
    private static final String OLD = "Admin@123456";
    private static final String NEW = "Matkhaumoi123";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean EmailSender mail;

    /** Mail gửi ở luồng nền nên danh sách cần đồng bộ. */
    private final List<EmailMessage> sent = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void reset() {
        sent.clear();
        doAnswer(call -> sent.add(call.getArgument(0))).when(mail).send(any());
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from login_sessions");
        jdbc.update("delete from password_reset_requests");
        jdbc.update("update app_users set password_hash = ? where id = 1", passwordEncoder.encode(OLD));
    }

    @Test
    void resetsPasswordByEmailAndLogsOutEveryDevice() throws Exception {
        String phoneRefresh = login("phone-device-01", OLD);
        String laptopRefresh = login("laptop-device-1", OLD);

        String resetId = resetId(start("  Thanh.Nguyen@Example.com ")
            .andExpect(jsonPath("$.data.identifierType").value("EMAIL"))
            .andExpect(jsonPath("$.data.destination").value("th••••••••••@example.com"))
            .andExpect(jsonPath("$.data.remainingAttempts").value(5)));
        EmailMessage otpMail = awaitMail(1);
        assertEquals(EMAIL, otpMail.to());
        assertEquals("Mã đặt lại mật khẩu", otpMail.subject());

        String token = token(verify(resetId, code())
            .andExpect(jsonPath("$.data.email").value("th••••••••••@example.com")));

        complete(resetId, token, NEW, NEW)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revokedSessionCount").value(2))
            .andExpect(jsonPath("$.data.notifiedEmail").value("th••••••••••@example.com"))
            .andExpect(jsonPath("$.data.biometricDisabled").value(true));

        refreshStatus(phoneRefresh).andExpect(status().isUnauthorized());
        refreshStatus(laptopRefresh).andExpect(status().isUnauthorized());
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from login_sessions where revoked_reason = 'PASSWORD_RESET'", Integer.class));
        loginStatus(null, OLD).andExpect(status().isUnauthorized());
        loginStatus(null, NEW).andExpect(status().isOk());
        assertEquals("Mật khẩu của bạn đã được đặt lại", awaitMail(2).subject());

        // Token chỉ dùng một lần.
        complete(resetId, token, "Khac12345678", "Khac12345678")
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.code").value(1022));
    }

    @Test
    void phoneNumberSendsCodeToRegisteredEmail() throws Exception {
        String resetId = resetId(start("090 123 4567")
            .andExpect(jsonPath("$.data.identifierType").value("PHONE"))
            .andExpect(jsonPath("$.data.destination").value("090•••••67")));
        assertEquals(EMAIL, awaitMail(1).to());
        verify(resetId, code()).andExpect(status().isOk());
    }

    @Test
    void unknownAccountLooksTheSameButNeverWorks() throws Exception {
        String resetId = resetId(start("khong.ton.tai@example.com")
            .andExpect(jsonPath("$.data.identifierType").value("EMAIL"))
            .andExpect(jsonPath("$.data.destination").value("kh•••••••••••@example.com"))
            .andExpect(jsonPath("$.data.remainingAttempts").value(5)));
        start("0999999999").andExpect(status().isCreated());

        verify(resetId, "123456")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1010))
            .andExpect(jsonPath("$.message").value("Mã xác thực không đúng. Bạn còn 4 lần thử"));
        complete(resetId, "bat-ky", NEW, NEW).andExpect(jsonPath("$.code").value(1023));
        Thread.sleep(300);
        assertTrue(sent.isEmpty());
    }

    @Test
    void requiresVerifiedCodeAndMatchingToken() throws Exception {
        String resetId = resetId(start(EMAIL));
        awaitMail(1);
        complete(resetId, "chua-xac-minh", NEW, NEW)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(1023));

        String token = token(verify(resetId, code()));
        complete(resetId, token + "x", NEW, NEW)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(1024));
        complete(resetId, token, NEW, "Khac12345678").andExpect(jsonPath("$.code").value(1008));
        complete(resetId, token, "yeu", "yeu").andExpect(jsonPath("$.code").value(1009));
        complete(resetId, token, OLD, OLD).andExpect(jsonPath("$.code").value(1017));
        // Lỗi kiểm tra mật khẩu không làm mất token.
        complete(resetId, token, NEW, NEW).andExpect(status().isOk());
    }

    @Test
    void wrongCodeIsCountedAndCodeCanBeResentAfterCooldown() throws Exception {
        String resetId = resetId(start(EMAIL));
        awaitMail(1);
        String wrong = code().equals("000000") ? "111111" : "000000";
        verify(resetId, wrong).andExpect(jsonPath("$.message").value("Mã xác thực không đúng. Bạn còn 4 lần thử"));

        resend(resetId).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(1013));
        jdbc.update("update password_reset_requests set otp_sent_at = otp_sent_at - interval '1 minute'");
        resend(resetId).andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingAttempts").value(5));
        awaitMail(2);
        verify(resetId, code()).andExpect(status().isOk());
    }

    @Test
    void emailChangedWhileWaitingInvalidatesRequest() throws Exception {
        String resetId = resetId(start(EMAIL));
        awaitMail(1);
        String token = token(verify(resetId, code()));
        jdbc.update("update app_users set email = 'moi-reset@example.com' where id = 1");
        try {
            complete(resetId, token, NEW, NEW).andExpect(jsonPath("$.code").value(1022));
        } finally {
            jdbc.update("update app_users set email = ? where id = 1", EMAIL);
        }
        loginStatus(null, OLD).andExpect(status().isOk());
    }

    @Test
    void limitsRequestsPerIdentifier() throws Exception {
        for (int i = 0; i < 5; i++) start("0911222333").andExpect(status().isCreated());
        start("0911 222 333")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(1014));
    }

    @Test
    void rejectsInvalidIdentifier() throws Exception {
        start("abc").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1025));
        start("a@b").andExpect(jsonPath("$.code").value(1025));
        start("").andExpect(status().isBadRequest());
    }

    // ---- Helpers ----

    private ResultActions start(String identifier) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\"}"));
    }

    private ResultActions resend(String resetId) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-resets/{id}/otp/resend", resetId));
    }

    private ResultActions verify(String resetId, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-resets/{id}/verify", resetId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions complete(String resetId, String token, String next, String confirm) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-resets/{id}/complete", resetId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"resetToken":"%s","newPassword":"%s","confirmPassword":"%s"}""".formatted(token, next, confirm)));
    }

    private String resetId(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.data.resetId");
    }

    private String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.resetToken");
    }

    private String login(String deviceId, String password) throws Exception {
        String body = loginStatus(deviceId, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.refreshToken");
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

    /** Chờ tới khi đã có ít nhất {@code count} email, trả email mới nhất. */
    private EmailMessage awaitMail(int count) throws InterruptedException {
        for (int i = 0; i < 100 && sent.size() < count; i++) Thread.sleep(20);
        assertTrue(sent.size() >= count, "Chưa nhận đủ " + count + " email");
        return sent.get(sent.size() - 1);
    }

    private String code() {
        synchronized (sent) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                var matcher = CODE.matcher(sent.get(i).textBody());
                if (matcher.find()) return matcher.group(1);
            }
        }
        throw new AssertionError("Chưa có email chứa mã");
    }
}
