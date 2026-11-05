package com.thanh.workhub.auth;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.application.AuthService;
import com.thanh.workhub.auth.internal.application.RefreshTokenService;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.user.internal.domain.User;
import com.thanh.workhub.user.internal.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final User user = mock(User.class);
    private final AuthService service = new AuthService(users, passwords, jwt, refresh);

    @BeforeEach
    void setUp() {
        when(user.getPasswordHash()).thenReturn("hash");
        when(passwords.matches("secret", "hash")).thenReturn(true);
    }

    @Test
    void rejectsUnverifiedEmailWithoutIssuingTokens() {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        var error = assertThrows(ResponseStatusException.class,
            () -> service.login(new LoginRequest(" USER@EXAMPLE.COM ", "secret")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(jwt, refresh);
    }

    @Test
    void allowsVerifiedEmail() {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(user.isEmailVerified()).thenReturn(true);
        service.login(new LoginRequest("user@example.com", "secret"));
        verify(jwt).generateAccessToken(user);
        verify(refresh).create(user.getId());
    }

    @Test
    void allowsPhoneWithoutVerifiedEmail() {
        when(users.findByPhone("0901234567")).thenReturn(Optional.of(user));
        service.login(new LoginRequest(" 0901234567 ", "secret"));
        verify(jwt).generateAccessToken(user);
        verify(refresh).create(user.getId());
        verify(user, never()).isEmailVerified();
    }

    @Test
    void rejectsWrongPasswordBeforeCheckingVerification() {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong", "hash")).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class,
            () -> service.login(new LoginRequest("user@example.com", "wrong")));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verify(user, never()).isEmailVerified();
        verifyNoInteractions(jwt, refresh);
    }

    @Test
    void rejectsUnknownPhone() {
        when(users.findByPhone("0901234567")).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class,
            () -> service.login(new LoginRequest("0901234567", "secret")));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(jwt, refresh);
    }
}
