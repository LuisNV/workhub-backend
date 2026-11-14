package com.thanh.workhub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.application.AuthService;
import com.thanh.workhub.auth.internal.application.RefreshTokenService;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

class AuthServiceTest {
    private static final UserCredentials UNVERIFIED =
            new UserCredentials(5L, "user@example.com", "hash", false);
    private static final UserCredentials VERIFIED =
            new UserCredentials(5L, "user@example.com", "hash", true);

    private final UserApi users = mock(UserApi.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final AuthService service = new AuthService(users, passwords, jwt, refresh);

    @BeforeEach
    void setUp() {
        when(passwords.matches("secret", "hash")).thenReturn(true);
    }

    @Test
    void rejectsUnverifiedEmailWithoutIssuingTokens() {
        when(users.findCredentialsByEmail("user@example.com")).thenReturn(Optional.of(UNVERIFIED));
        var error = assertThrows(ApiException.class,
            () -> service.login(new LoginRequest(" USER@EXAMPLE.COM ", "secret")));
        assertEquals(ErrorCode.EMAIL_NOT_VERIFIED, error.getErrorCode());
        verifyNoInteractions(jwt, refresh);
    }

    @Test
    void allowsVerifiedEmail() {
        when(users.findCredentialsByEmail("user@example.com")).thenReturn(Optional.of(VERIFIED));
        service.login(new LoginRequest("user@example.com", "secret"));
        verify(jwt).generateAccessToken(5L, "user@example.com");
        verify(refresh).create(5L);
    }

    @Test
    void allowsPhoneWithoutVerifiedEmail() {
        when(users.findCredentialsByPhone("0901234567")).thenReturn(Optional.of(UNVERIFIED));
        service.login(new LoginRequest(" 0901234567 ", "secret"));
        verify(jwt).generateAccessToken(5L, "user@example.com");
        verify(refresh).create(5L);
    }

    @Test
    void rejectsWrongPasswordBeforeCheckingVerification() {
        // User chưa xác thực email + sai mật khẩu: phải báo sai thông tin đăng nhập, không lộ trạng thái email.
        when(users.findCredentialsByEmail("user@example.com")).thenReturn(Optional.of(UNVERIFIED));
        when(passwords.matches("wrong", "hash")).thenReturn(false);
        var error = assertThrows(ApiException.class,
            () -> service.login(new LoginRequest("user@example.com", "wrong")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
        verifyNoInteractions(jwt, refresh);
    }

    @Test
    void rejectsUnknownPhone() {
        when(users.findCredentialsByPhone("0901234567")).thenReturn(Optional.empty());
        var error = assertThrows(ApiException.class,
            () -> service.login(new LoginRequest("0901234567", "secret")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
        verifyNoInteractions(jwt, refresh);
    }
}
