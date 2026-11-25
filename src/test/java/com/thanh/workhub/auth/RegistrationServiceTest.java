package com.thanh.workhub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thanh.workhub.auth.internal.api.dto.ChangeRegistrationEmailRequest;
import com.thanh.workhub.auth.internal.api.dto.InitiateRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.StartRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.VerifyRegistrationRequest;
import com.thanh.workhub.auth.internal.application.RefreshTokenService;
import com.thanh.workhub.auth.internal.application.RegistrationService;
import com.thanh.workhub.auth.internal.domain.RegistrationSession;
import com.thanh.workhub.auth.internal.persistence.RegistrationOtpSendRepository;
import com.thanh.workhub.auth.internal.persistence.RegistrationSessionRepository;
import com.thanh.workhub.platform.mail.EmailMessage;
import com.thanh.workhub.platform.mail.EmailSender;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.NewUser;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;
import com.thanh.workhub.user.UserProfileResponse;

class RegistrationServiceTest {
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-25T08:00:00Z"));
    private final Map<UUID, RegistrationSession> store = new HashMap<>();
    private final RegistrationSessionRepository sessions = mock(RegistrationSessionRepository.class);
    private final RegistrationOtpSendRepository otpSends = mock(RegistrationOtpSendRepository.class);
    private final UserApi users = mock(UserApi.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final EmailSender mail = mock(EmailSender.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final List<EmailMessage> sent = new ArrayList<>();

    private final RegistrationService service = new RegistrationService(sessions, otpSends, users, passwords,
            mail, jwt, refresh, clock, 60, 10, 45, 5, 5, 10);

    @BeforeEach
    void setUp() {
        when(sessions.save(any())).thenAnswer(call -> {
            RegistrationSession session = call.getArgument(0);
            store.put(session.getId(), session);
            return session;
        });
        when(sessions.findForUpdate(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0))));
        when(users.findCredentialsByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findCredentialsByPhone(anyString())).thenReturn(Optional.empty());
        when(passwords.encode(anyString())).thenAnswer(call -> "hash:" + call.getArgument(0));
        org.mockito.Mockito.doAnswer(call -> sent.add(call.getArgument(0))).when(mail).send(any());
    }

    // ---- Bước 1 ----

    @Test
    void startNormalizesEmailAndPhone() {
        var result = start();
        assertEquals("minhanh@gmail.com", result.email());
        assertEquals("0912345678", result.phone());
        assertEquals("SET_PASSWORD", result.nextStep());
        verify(mail, never()).send(any());
    }

    @Test
    void startRejectsUsedEmailAndPhone() {
        var existing = new UserCredentials(1L, "minhanh@gmail.com", "h", true);
        when(users.findCredentialsByEmail("minhanh@gmail.com")).thenReturn(Optional.of(existing));
        assertEquals(ErrorCode.EMAIL_ALREADY_USED, assertThrows(ApiException.class, this::start).getErrorCode());

        when(users.findCredentialsByEmail("minhanh@gmail.com")).thenReturn(Optional.empty());
        when(users.findCredentialsByPhone("0912345678")).thenReturn(Optional.of(existing));
        assertEquals(ErrorCode.PHONE_ALREADY_USED, assertThrows(ApiException.class, this::start).getErrorCode());
    }

    // ---- Bước 2 ----

    @Test
    void initiateRejectsWeakOrMismatchedPassword() {
        var id = start().registrationId();
        assertEquals(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH, assertThrows(ApiException.class,
                () -> service.initiate(id, new InitiateRegistrationRequest("Abcdef12", "Abcdef13", false)))
                .getErrorCode());
        for (String weak : List.of("Abc12", "abcdefg1", "ABCDEFG1", "Abcdefgh")) {
            assertEquals(ErrorCode.PASSWORD_TOO_WEAK, assertThrows(ApiException.class,
                    () -> service.initiate(id, new InitiateRegistrationRequest(weak, weak, false))).getErrorCode(),
                    weak);
        }
        verify(mail, never()).send(any());
    }

    @Test
    void initiateHashesPasswordAndEmailsSixDigitCode() {
        var id = start().registrationId();
        var result = service.initiate(id, new InitiateRegistrationRequest("Matkhau123", "Matkhau123", true));

        assertEquals("VERIFY_EMAIL", result.nextStep());
        assertTrue(result.biometricEnabled());
        assertEquals(clock.instant().plus(Duration.ofMinutes(10)), result.otpExpiresAt());
        assertEquals(clock.instant().plusSeconds(45), result.resendAvailableAt());
        assertEquals("hash:Matkhau123", store.get(id).getPasswordHash());
        assertEquals("minhanh@gmail.com", sent.get(0).to());
        assertTrue(CODE.matcher(sent.get(0).textBody()).find());
        // Mã gốc không được lưu.
        assertFalse(store.get(id).getOtpHash().contains(lastCode()));
    }

    @Test
    void goingBackToChangePasswordDoesNotSendAnotherEmail() {
        var id = startAndInitiate();
        service.initiate(id, new InitiateRegistrationRequest("Khac12345", "Khac12345", false));
        assertEquals(1, sent.size());
        assertEquals("hash:Khac12345", store.get(id).getPasswordHash());
    }

    // ---- Bước 3 ----

    @Test
    void wrongCodeCountsDownThenLocksUntilResend() {
        var id = startAndInitiate();
        String wrong = lastCode().equals("000000") ? "111111" : "000000";
        for (int remaining = 4; remaining >= 1; remaining--) {
            var error = assertThrows(ApiException.class, () -> verifyCode(id, wrong));
            assertEquals(ErrorCode.OTP_INVALID, error.getErrorCode());
            assertTrue(error.getMessage().contains("còn " + remaining + " lần"), error.getMessage());
        }
        assertEquals(ErrorCode.OTP_TOO_MANY_ATTEMPTS,
                assertThrows(ApiException.class, () -> verifyCode(id, wrong)).getErrorCode());
        // Đã khóa: kể cả mã đúng cũng bị từ chối.
        assertEquals(ErrorCode.OTP_TOO_MANY_ATTEMPTS,
                assertThrows(ApiException.class, () -> verifyCode(id, lastCode())).getErrorCode());

        clock.advance(Duration.ofSeconds(45));
        service.resendOtp(id);
        assertEquals("COMPLETE", verifyCode(id, lastCode()).nextStep());
    }

    @Test
    void expiredCodeIsRejected() {
        var id = startAndInitiate();
        clock.advance(Duration.ofMinutes(10));
        assertEquals(ErrorCode.OTP_EXPIRED,
                assertThrows(ApiException.class, () -> verifyCode(id, lastCode())).getErrorCode());
    }

    @Test
    void resendRespectsCooldownAndInvalidatesOldCode() {
        var id = startAndInitiate();
        String first = lastCode();
        clock.advance(Duration.ofSeconds(30));
        var error = assertThrows(ApiException.class, () -> service.resendOtp(id));
        assertEquals(ErrorCode.OTP_RESEND_TOO_SOON, error.getErrorCode());
        assertTrue(error.getMessage().contains("15 giây"), error.getMessage());

        clock.advance(Duration.ofSeconds(15));
        service.resendOtp(id);
        assertEquals(2, sent.size());
        if (!first.equals(lastCode())) {
            assertThrows(ApiException.class, () -> verifyCode(id, first));
        }
    }

    @Test
    void stopsSendingAfterPerSessionLimit() {
        var id = startAndInitiate();
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofSeconds(45));
            service.resendOtp(id);
        }
        clock.advance(Duration.ofSeconds(45));
        assertEquals(ErrorCode.OTP_SEND_LIMIT_REACHED,
                assertThrows(ApiException.class, () -> service.resendOtp(id)).getErrorCode());
        assertEquals(5, sent.size());
    }

    @Test
    void stopsSendingWhenEmailReceivedTooManyCodesAcrossSessions() {
        when(otpSends.countByEmailAndSentAtAfter(any(), any())).thenReturn(10L);
        var id = start().registrationId();
        assertEquals(ErrorCode.OTP_SEND_LIMIT_REACHED, assertThrows(ApiException.class,
                () -> service.initiate(id, new InitiateRegistrationRequest("Matkhau123", "Matkhau123", false)))
                .getErrorCode());
        verify(mail, never()).send(any());
    }

    @Test
    void changeEmailSendsNewCodeToNewAddressAndKillsOldCode() {
        var id = startAndInitiate();
        String oldCode = lastCode();
        var result = service.changeEmail(id, new ChangeRegistrationEmailRequest(" Moi@Gmail.com "));

        assertEquals("moi@gmail.com", result.email());
        assertEquals("moi@gmail.com", sent.get(1).to());
        if (!oldCode.equals(lastCode())) {
            assertThrows(ApiException.class, () -> verifyCode(id, oldCode));
        }
        assertEquals("COMPLETE", verifyCode(id, lastCode()).nextStep());
    }

    // ---- Bước 4 ----

    @Test
    void completeRequiresVerifiedEmail() {
        var id = startAndInitiate();
        assertEquals(ErrorCode.REGISTRATION_INVALID_STEP,
                assertThrows(ApiException.class, () -> service.complete(id)).getErrorCode());
        verify(users, never()).register(any());
    }

    @Test
    void completeCreatesUserAndLogsIn() {
        var id = startAndInitiate();
        verifyCode(id, lastCode());
        when(users.register(any())).thenReturn(new UserProfileResponse(42L, "Nguyễn Minh Anh",
                "minhanh@gmail.com", "0912345678", null, null, LocalDate.of(2002, 3, 12), null, true));
        when(jwt.generateAccessToken(42L, "minhanh@gmail.com")).thenReturn("access");
        when(refresh.create(42L)).thenReturn("refresh");
        when(jwt.getExpiresInSeconds()).thenReturn(900L);

        var result = service.complete(id);

        var created = ArgumentCaptor.forClass(NewUser.class);
        verify(users).register(created.capture());
        assertEquals(new NewUser("Nguyễn Minh Anh", "minhanh@gmail.com", "0912345678",
                LocalDate.of(2002, 3, 12), "hash:Matkhau123"), created.getValue());
        assertEquals("access", result.tokens().accessToken());
        assertEquals("refresh", result.tokens().refreshToken());
        assertTrue(result.biometricEnabled());

        // Không hoàn tất được lần hai.
        assertEquals(ErrorCode.REGISTRATION_INVALID_STEP,
                assertThrows(ApiException.class, () -> service.complete(id)).getErrorCode());
        verify(users, times(1)).register(any());
    }

    @Test
    void expiredSessionCannotContinue() {
        var id = start().registrationId();
        clock.advance(Duration.ofMinutes(60));
        assertEquals(ErrorCode.REGISTRATION_EXPIRED, assertThrows(ApiException.class,
                () -> service.initiate(id, new InitiateRegistrationRequest("Matkhau123", "Matkhau123", false)))
                .getErrorCode());
    }

    @Test
    void unknownSessionIsNotFound() {
        assertEquals(ErrorCode.REGISTRATION_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.resendOtp(UUID.randomUUID())).getErrorCode());
    }

    // ---- Helpers ----

    private com.thanh.workhub.auth.internal.api.dto.RegistrationResponse start() {
        return service.start(new StartRegistrationRequest("  Nguyễn Minh Anh ", " MinhAnh@Gmail.com ",
                "091 234 5678", LocalDate.of(2002, 3, 12)));
    }

    private UUID startAndInitiate() {
        var id = start().registrationId();
        service.initiate(id, new InitiateRegistrationRequest("Matkhau123", "Matkhau123", true));
        return id;
    }

    private com.thanh.workhub.auth.internal.api.dto.RegistrationResponse verifyCode(UUID id, String code) {
        return service.verify(id, new VerifyRegistrationRequest(code));
    }

    private String lastCode() {
        var matcher = CODE.matcher(sent.get(sent.size() - 1).textBody());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
