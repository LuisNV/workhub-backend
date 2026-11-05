package com.thanh.workhub.user;

import com.thanh.workhub.platform.security.SecurityConfig;
import com.thanh.workhub.user.internal.api.UserController;
import com.thanh.workhub.user.internal.application.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserController.class, properties =
    "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import(SecurityConfig.class)
class CorsTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;

    @Test
    void allowsLocalFrontendPreflightWithoutToken() throws Exception {
        for (String origin : new String[]{"http://localhost:3000", "http://127.0.0.1:5173"}) {
            mvc.perform(options("/api/v1/auth/login")
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void protectedApiStillRequiresTokenAndReturnsCorsHeaders() throws Exception {
        mvc.perform(get("/api/v1/users/1").header("Origin", "http://localhost:3000"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }
}
