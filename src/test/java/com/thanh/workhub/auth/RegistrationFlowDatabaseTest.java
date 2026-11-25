package com.thanh.workhub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.thanh.workhub.platform.mail.EmailMessage;
import com.thanh.workhub.platform.mail.EmailSender;

/** Chạy toàn bộ luồng đăng ký qua HTTP trên Postgres thật; chỉ thay phần gửi mail. */
@SpringBootTest(properties = "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class RegistrationFlowDatabaseTest {
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailSender mail;

    private final List<EmailMessage> sent = new ArrayList<>();

    @BeforeEach
    void captureMail() {
        sent.clear();
        doAnswer(call -> sent.add(call.getArgument(0))).when(mail).send(any());
    }

    @Test
    void fullFlowCreatesVerifiedUserWhoCanLogIn() throws Exception {
        // Bước 1
        String id = JsonPath.read(mvc.perform(post("/api/v1/auth/registrations")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"fullName":"Nguyễn Minh Anh","email":"MinhAnh@Gmail.com",
                     "phone":"091 234 5678","dateOfBirth":"2002-03-12"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.nextStep").value("SET_PASSWORD"))
            .andReturn().getResponse().getContentAsString(), "$.data.registrationId");

        // Bước 2
        mvc.perform(post("/api/v1/auth/registrations/{id}/initiate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Matkhau123\",\"confirmPassword\":\"Matkhau123\",\"biometricEnabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextStep").value("VERIFY_EMAIL"))
            .andExpect(jsonPath("$.data.remainingAttempts").value(5));
        assertEquals("minhanh@gmail.com", sent.get(0).to());
        String code = code();

        // Bước 3: nhập sai một lần, lượt sai phải được lưu xuống DB dù request trả lỗi.
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/v1/auth/registrations/{id}/verify", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + wrong + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1010));
        assertEquals(1, jdbc.queryForObject(
                "select otp_attempts from registration_sessions where id = ?::uuid", Integer.class, id));

        mvc.perform(post("/api/v1/auth/registrations/{id}/verify", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextStep").value("COMPLETE"))
            .andExpect(jsonPath("$.data.emailVerified").value(true));

        // Bước 4
        mvc.perform(post("/api/v1/auth/registrations/{id}/complete", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.email").value("minhanh@gmail.com"))
            .andExpect(jsonPath("$.data.user.phone").value("0912345678"))
            .andExpect(jsonPath("$.data.user.emailVerified").value(true))
            .andExpect(jsonPath("$.data.biometricEnabled").value(true))
            .andExpect(jsonPath("$.data.tokens.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokens.refreshToken").isNotEmpty());

        mvc.perform(get("/api/v1/auth/registrations/{id}", id))
            .andExpect(jsonPath("$.data.nextStep").value("DONE"));

        // Đăng nhập bằng email, và bằng số điện thoại có khoảng trắng.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"minhanh@gmail.com\",\"password\":\"Matkhau123\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"091 234 5678\",\"password\":\"Matkhau123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsEmailOrPhoneOfExistingUser() throws Exception {
        // Seed user (V3) có phone "090 123 4567", đã được V13 chuẩn hóa về "0901234567".
        mvc.perform(post("/api/v1/auth/registrations").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"A","email":"THANH.NGUYEN@example.com","phone":"0999999999"}"""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(1003));
        mvc.perform(post("/api/v1/auth/registrations").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"A","email":"other@example.com","phone":"090 123 4567"}"""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void validatesPhoneFormat() throws Exception {
        mvc.perform(post("/api/v1/auth/registrations").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"A","email":"a@example.com","phone":"091 234 567"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void changeEmailSendsCodeToNewAddress() throws Exception {
        String id = JsonPath.read(mvc.perform(post("/api/v1/auth/registrations")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"fullName":"B","email":"b-old@example.com","phone":"0977777777"}"""))
            .andReturn().getResponse().getContentAsString(), "$.data.registrationId");
        mvc.perform(post("/api/v1/auth/registrations/{id}/initiate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Matkhau123\",\"confirmPassword\":\"Matkhau123\"}"))
            .andExpect(status().isOk());

        mvc.perform(put("/api/v1/auth/registrations/{id}/email", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"b-new@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("b-new@example.com"))
            .andExpect(jsonPath("$.data.nextStep").value("VERIFY_EMAIL"));
        assertEquals("b-new@example.com", sent.get(1).to());
    }

    @Test
    void resendTooSoonIs429() throws Exception {
        String id = JsonPath.read(mvc.perform(post("/api/v1/auth/registrations")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"fullName":"C","email":"c@example.com","phone":"0966666666"}"""))
            .andReturn().getResponse().getContentAsString(), "$.data.registrationId");
        mvc.perform(post("/api/v1/auth/registrations/{id}/initiate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Matkhau123\",\"confirmPassword\":\"Matkhau123\"}"));
        mvc.perform(post("/api/v1/auth/registrations/{id}/otp/resend", id))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(1013));
    }

    @Test
    void unknownSessionIs404() throws Exception {
        mvc.perform(get("/api/v1/auth/registrations/{id}", "00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(1005));
    }

    private String code() {
        var matcher = CODE.matcher(sent.get(sent.size() - 1).textBody());
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
