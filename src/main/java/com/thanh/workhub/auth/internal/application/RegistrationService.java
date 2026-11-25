package com.thanh.workhub.auth.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.api.dto.ChangeRegistrationEmailRequest;
import com.thanh.workhub.auth.internal.api.dto.CompleteRegistrationResponse;
import com.thanh.workhub.auth.internal.api.dto.InitiateRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.auth.internal.api.dto.RegistrationResponse;
import com.thanh.workhub.auth.internal.api.dto.StartRegistrationRequest;
import com.thanh.workhub.auth.internal.api.dto.VerifyRegistrationRequest;
import com.thanh.workhub.auth.internal.domain.RegistrationOtpSend;
import com.thanh.workhub.auth.internal.domain.RegistrationSession;
import com.thanh.workhub.auth.internal.domain.RegistrationStatus;
import com.thanh.workhub.auth.internal.persistence.RegistrationOtpSendRepository;
import com.thanh.workhub.auth.internal.persistence.RegistrationSessionRepository;
import com.thanh.workhub.platform.mail.EmailSender;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.NewUser;
import com.thanh.workhub.user.UserApi;

/**
 * Luồng đăng ký 4 bước:
 * <ol>
 *   <li>{@link #start}: tạo phiên với họ tên, email, số điện thoại, ngày sinh.</li>
 *   <li>{@link #initiate}: đặt mật khẩu, gửi mã 6 số về email qua SMTP.</li>
 *   <li>{@link #verify}: nhập mã. Có {@link #resendOtp} và {@link #changeEmail}.</li>
 *   <li>{@link #complete}: tạo user (email đã xác thực) và trả token đăng nhập.</li>
 * </ol>
 * Gửi email nằm trong transaction: gửi lỗi thì rollback, mã không được ghi nhận và không tính lượt gửi.
 */
@Service
public class RegistrationService {
    private final RegistrationSessionRepository sessions;
    private final RegistrationOtpSendRepository otpSends;
    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private final Duration sessionTtl;
    private final Duration otpTtl;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final int maxSendsPerSession;
    private final int maxSendsPerEmailPerHour;

    public RegistrationService(RegistrationSessionRepository sessions, RegistrationOtpSendRepository otpSends,
            UserApi userApi, PasswordEncoder passwordEncoder, EmailSender emailSender, JwtService jwtService,
            RefreshTokenService refreshTokenService, Clock clock,
            @Value("${app.registration.session-ttl-minutes}") long sessionTtlMinutes,
            @Value("${app.registration.otp-ttl-minutes}") long otpTtlMinutes,
            @Value("${app.registration.otp-resend-cooldown-seconds}") long resendCooldownSeconds,
            @Value("${app.registration.otp-max-attempts}") int maxAttempts,
            @Value("${app.registration.otp-max-sends}") int maxSendsPerSession,
            @Value("${app.registration.otp-max-sends-per-email-per-hour}") int maxSendsPerEmailPerHour) {
        this.sessions = sessions;
        this.otpSends = otpSends;
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
        this.sessionTtl = Duration.ofMinutes(sessionTtlMinutes);
        this.otpTtl = Duration.ofMinutes(otpTtlMinutes);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.maxAttempts = maxAttempts;
        this.maxSendsPerSession = maxSendsPerSession;
        this.maxSendsPerEmailPerHour = maxSendsPerEmailPerHour;
    }

    // ---- Bước 1: tạo phiên ----

    @Transactional
    public RegistrationResponse start(StartRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        ensureEmailAvailable(email);
        if (userApi.findCredentialsByPhone(phone).isPresent()) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_USED);
        }
        var session = RegistrationSession.start(request.fullName().strip(), email, phone,
                request.dateOfBirth(), now(), sessionTtl);
        sessions.save(session);
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public RegistrationResponse get(UUID id) {
        var session = sessions.findById(id).orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
        if (session.getStatus() != RegistrationStatus.COMPLETED) session.ensureUsable(now());
        return toResponse(session);
    }

    // ---- Bước 2: khởi tạo (đặt mật khẩu + gửi mã) ----

    @Transactional
    public RegistrationResponse initiate(UUID id, InitiateRegistrationRequest request) {
        var session = load(id);
        validatePassword(request.password(), request.confirmPassword());
        session.setPassword(passwordEncoder.encode(request.password()), request.biometricEnabled());
        // Quay lại bước 2 để đổi mật khẩu thì giữ mã đã gửi, không gửi thêm mail.
        if (session.getStatus() == RegistrationStatus.CREATED) {
            sendOtp(session);
        }
        return toResponse(session);
    }

    // ---- Bước 3: xác thực email ----

    @Transactional
    public RegistrationResponse resendOtp(UUID id) {
        var session = load(id);
        session.ensureStatus(RegistrationStatus.OTP_SENT);
        var availableAt = session.getOtpSentAt().plus(resendCooldown);
        if (now().isBefore(availableAt)) {
            long seconds = (Duration.between(now(), availableAt).toMillis() + 999) / 1000; // làm tròn lên
            throw new ApiException(ErrorCode.OTP_RESEND_TOO_SOON,
                    "Vui lòng chờ " + seconds + " giây trước khi gửi lại mã");
        }
        sendOtp(session);
        return toResponse(session);
    }

    @Transactional
    public RegistrationResponse changeEmail(UUID id, ChangeRegistrationEmailRequest request) {
        var session = load(id);
        String email = normalizeEmail(request.email());
        if (email.equals(session.getEmail())) return toResponse(session);
        ensureEmailAvailable(email);
        session.changeEmail(email);
        // Đã có mật khẩu thì gửi mã tới email mới ngay (không áp cooldown, vẫn tính vào giới hạn gửi).
        if (session.getPasswordHash() != null) {
            sendOtp(session);
        }
        return toResponse(session);
    }

    /** Lần nhập sai phải được lưu lại dù ném lỗi, nên không rollback khi gặp ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public RegistrationResponse verify(UUID id, VerifyRegistrationRequest request) {
        var session = load(id);
        // Client gửi lại request sau khi đã xác thực thành công: trả kết quả cũ.
        if (session.getStatus() == RegistrationStatus.VERIFIED) return toResponse(session);
        session.ensureStatus(RegistrationStatus.OTP_SENT);
        if (session.getOtpAttempts() >= maxAttempts) throw new ApiException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        if (!now().isBefore(session.getOtpExpiresAt())) throw new ApiException(ErrorCode.OTP_EXPIRED);

        byte[] expected = session.getOtpHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hashOtp(session.getId(), request.code()).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            session.otpFailed();
            int remaining = maxAttempts - session.getOtpAttempts();
            if (remaining <= 0) throw new ApiException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
            throw new ApiException(ErrorCode.OTP_INVALID,
                    "Mã xác thực không đúng. Bạn còn " + remaining + " lần thử");
        }
        session.markVerified(now());
        return toResponse(session);
    }

    // ---- Bước 4: hoàn tất ----

    @Transactional
    public CompleteRegistrationResponse complete(UUID id) {
        var session = load(id);
        session.ensureStatus(RegistrationStatus.VERIFIED);
        // UserApi kiểm tra trùng lần nữa: email/số điện thoại có thể đã bị người khác đăng ký trong lúc chờ.
        var user = userApi.register(new NewUser(session.getFullName(), session.getEmail(), session.getPhone(),
                session.getDateOfBirth(), session.getPasswordHash()));
        session.complete(user.id());

        String accessToken = jwtService.generateAccessToken(user.id(), user.email());
        String refreshToken = refreshTokenService.create(user.id());
        var tokens = new LoginResponse(accessToken, refreshToken, "Bearer", jwtService.getExpiresInSeconds());
        return new CompleteRegistrationResponse(user, session.isBiometricEnabled(), tokens);
    }

    // ---- Hỗ trợ ----

    private RegistrationSession load(UUID id) {
        var session = sessions.findForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.REGISTRATION_NOT_FOUND));
        session.ensureUsable(now());
        return session;
    }

    private void sendOtp(RegistrationSession session) {
        if (session.getOtpSendCount() >= maxSendsPerSession) {
            throw new ApiException(ErrorCode.OTP_SEND_LIMIT_REACHED);
        }
        Instant now = now();
        if (otpSends.countByEmailAndSentAtAfter(session.getEmail(), now.minus(Duration.ofHours(1)))
                >= maxSendsPerEmailPerHour) {
            throw new ApiException(ErrorCode.OTP_SEND_LIMIT_REACHED,
                    "Email này đã nhận quá nhiều mã, vui lòng thử lại sau 1 giờ");
        }
        String code = "%06d".formatted(random.nextInt(1_000_000));
        session.otpIssued(hashOtp(session.getId(), code), now, otpTtl);
        otpSends.save(new RegistrationOtpSend(session.getEmail(), now));
        emailSender.send(RegistrationEmails.otp(session.getEmail(), session.getFullName(), code, otpTtl));
    }

    private void ensureEmailAvailable(String email) {
        if (userApi.findCredentialsByEmail(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
        }
    }

    private void validatePassword(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) throw new ApiException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        boolean strong = password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit);
        if (!strong) throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK);
        // BCrypt chỉ dùng 72 byte đầu; chữ có dấu chiếm nhiều byte nên kiểm tra theo byte.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_WEAK, "Mật khẩu quá dài");
        }
    }

    private RegistrationResponse toResponse(RegistrationSession session) {
        String nextStep = switch (session.getStatus()) {
            case CREATED -> "SET_PASSWORD";
            case OTP_SENT -> "VERIFY_EMAIL";
            case VERIFIED -> "COMPLETE";
            case COMPLETED -> "DONE";
        };
        boolean otpPending = session.getStatus() == RegistrationStatus.OTP_SENT;
        return new RegistrationResponse(
                session.getId(),
                session.getStatus().name(),
                nextStep,
                session.getFullName(),
                session.getEmail(),
                session.getPhone(),
                session.getDateOfBirth(),
                session.isBiometricEnabled(),
                session.getEmailVerifiedAt() != null,
                session.getExpiresAt(),
                otpPending ? session.getOtpExpiresAt() : null,
                otpPending ? session.getOtpSentAt().plus(resendCooldown) : null,
                otpPending ? Math.max(0, maxAttempts - session.getOtpAttempts()) : null);
    }

    private Instant now() {
        return clock.instant();
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** "091 234 5678" → "0912345678". */
    static String normalizePhone(String phone) {
        return phone.replaceAll("\\s+", "");
    }

    /** Gắn ID phiên vào hash để cùng một mã ở hai phiên khác nhau cho ra hash khác nhau. */
    static String hashOtp(UUID sessionId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((sessionId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
