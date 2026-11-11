package com.thanh.workhub.auth;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.thanh.workhub.auth.internal.api.AuthController;
import com.thanh.workhub.auth.internal.application.AuthService;
import com.thanh.workhub.auth.internal.application.RefreshTokenService;
import com.thanh.workhub.platform.security.SecurityConfig;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.UserService;

@WebMvcTest(value = AuthController.class, properties =
        "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import(SecurityConfig.class)
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean RefreshTokenService refreshTokens;
    @MockitoBean UserService users;

    @Test void readsProfileByIdEvenWhenTokenContainsOldEmail() throws Exception {
        when(users.getProfile(7L)).thenReturn(new UserProfileResponse(
                7L, "Name", "new@example.com", "123", "Address", null, null, null, true));
        mvc.perform(get("/api/v1/auth/me").with(jwt()
                .jwt(token -> token.subject("7").claim("email", "old@example.com"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("new@example.com"));
        verifyNoInteractions(auth);
    }
}
