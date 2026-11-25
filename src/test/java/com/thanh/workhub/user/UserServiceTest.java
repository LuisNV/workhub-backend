package com.thanh.workhub.user;

import java.time.LocalDate;
import java.util.Optional;
import com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest;
import com.thanh.workhub.user.internal.application.*;
import com.thanh.workhub.user.internal.domain.User;
import com.thanh.workhub.user.internal.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    /** Limiter giả: không khóa, sai mật khẩu trả lỗi 1002 như thật. */
    private static PasswordAttemptLimiter limiter() {
        var limiter = mock(PasswordAttemptLimiter.class);
        when(limiter.recordFailure(any())).thenReturn(new com.thanh.workhub.platform.web.ApiException(
                com.thanh.workhub.platform.web.ErrorCode.CURRENT_PASSWORD_INCORRECT));
        return limiter;
    }

    @Test void updatesProfileAndPreservesEmailAndAvatar() {
        var repository = mock(UserRepository.class);
        var user = new User("Old", "original@example.com", "123", "Old address");
        user.updateAvatar("avatar-id", "https://example.com/avatar.png");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        var service = new UserService(repository, mock(org.springframework.security.crypto.password.PasswordEncoder.class), limiter());
        var result = service.updateProfile(7L, new UpdateProfileRequest(
                " New name ", " 0901234567 ", " New address ", "OTHER", LocalDate.of(2000, 1, 1)));
        assertEquals("New name", result.fullName());
        assertEquals("0901234567", result.phone());
        assertEquals("New address", result.address());
        assertEquals("OTHER", result.gender());
        assertEquals(LocalDate.of(2000, 1, 1), result.dateOfBirth());
        assertEquals("original@example.com", result.email());
        assertEquals("avatar-id", user.getAvatarPublicId());
        assertEquals("https://example.com/avatar.png", result.avatarUrl());
        verify(repository).saveAndFlush(user);
        service.updateProfile(7L, new UpdateProfileRequest("Name", "123", "Address", null, null));
        assertNull(user.getGender());
        assertNull(user.getDateOfBirth());
    }
    @Test void missingUserIsNotFound() {
        var repository = mock(UserRepository.class);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> new UserService(repository, mock(org.springframework.security.crypto.password.PasswordEncoder.class), limiter()).updateProfile(
                99L, new UpdateProfileRequest("Name", "123", "Address", null, null)));
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void emailChangeRequiresPasswordBeforeAnyMutation() {
        var repository = mock(UserRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var user = new User("Old", "old@example.com", "123", "Old address");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        var service = new UserService(repository, encoder, limiter());
        for (String password : new String[]{null, "", "   "}) {
            var exception = assertThrows(com.thanh.workhub.platform.web.ApiException.class,
                () -> service.updateProfile(7L, new UpdateProfileRequest(
                    "New", "456", "New address", null, null, "new@example.com", password)));
            assertEquals(1001, exception.getCode());
        }
        assertEquals("Old", user.getFullName());
        assertEquals("old@example.com", user.getEmail());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test void checksPasswordAndDuplicateBeforeChangingEmail() {
        var repository = mock(UserRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var user = new User("Old", "old@example.com", "123", "Old address");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        var service = new UserService(repository, encoder, limiter());
        var request = new UpdateProfileRequest("New", "456", "Address", null, null,
                "NEW@example.com", "password");
        assertEquals(1002, assertThrows(com.thanh.workhub.platform.web.ApiException.class,
                () -> service.updateProfile(7L, request)).getCode());
        when(encoder.matches("password", null)).thenReturn(true);
        when(repository.existsByEmail("new@example.com")).thenReturn(true);
        assertEquals(1003, assertThrows(com.thanh.workhub.platform.web.ApiException.class,
                () -> service.updateProfile(7L, request)).getCode());
        assertEquals("Old", user.getFullName());
        assertEquals("old@example.com", user.getEmail());
        verify(repository, never()).saveAndFlush(any());
        when(repository.existsByEmail("new@example.com")).thenReturn(false);
        var result = service.updateProfile(7L, request);
        assertEquals("new@example.com", result.email());
        assertFalse(user.isEmailVerified());
        verify(repository).saveAndFlush(user);
    }

    @Test void unchangedEmailDoesNotRequirePassword() {
        var repository = mock(UserRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var user = new User("Old", "old@example.com", "123", "Address");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        new UserService(repository, encoder, limiter()).updateProfile(7L, new UpdateProfileRequest(
                "New", "123", "Address", null, null, "OLD@example.com", null));
        verifyNoInteractions(encoder);
        verify(repository, never()).existsByEmail(any());
        assertEquals("New", user.getFullName());
    }
    @Test void duplicatePhoneDoesNotChangeProfileOrEmail() {
        var repository = mock(UserRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var user = new User("Old", "old@example.com", "123", "Address");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("password", null)).thenReturn(true);
        when(repository.existsByPhoneAndIdNot("456", 7L)).thenReturn(true);
        var error = assertThrows(com.thanh.workhub.platform.web.ApiException.class,
            () -> new UserService(repository, encoder, limiter()).updateProfile(7L, new UpdateProfileRequest(
                "New", " 456 ", "New address", null, null, "new@example.com", "password")));
        assertEquals(1004, error.getCode());
        assertEquals("Old", user.getFullName());
        assertEquals("123", user.getPhone());
        assertEquals("old@example.com", user.getEmail());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void unchangedPhoneExcludesCurrentUserFromDuplicateCheck() {
        var repository = mock(UserRepository.class);
        var user = new User("Old", "old@example.com", "123", "Address");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        new UserService(repository, mock(org.springframework.security.crypto.password.PasswordEncoder.class), limiter())
            .updateProfile(7L, new UpdateProfileRequest("New", " 123 ", "Address", null, null));
        verify(repository).existsByPhoneAndIdNot("123", 7L);
        verify(repository).saveAndFlush(user);
    }

    @Test void concurrentDuplicatePhoneReturnsSameBusinessCode() {
        var repository = mock(UserRepository.class);
        var user = new User("Old", "old@example.com", "123", "Address");
        when(repository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenThrow(new org.springframework.dao.DataIntegrityViolationException(
            "duplicate", new org.hibernate.exception.ConstraintViolationException(
                "duplicate", new java.sql.SQLException("duplicate", "23505"), "uk_app_users_phone")));
        var error = assertThrows(com.thanh.workhub.platform.web.ApiException.class,
            () -> new UserService(repository, mock(org.springframework.security.crypto.password.PasswordEncoder.class), limiter())
                .updateProfile(7L, new UpdateProfileRequest("Name", "456", "Address", null, null)));
        assertEquals(1004, error.getCode());
    }
}
