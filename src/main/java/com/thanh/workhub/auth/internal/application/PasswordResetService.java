package com.thanh.workhub.auth.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thanh.workhub.auth.internal.api.dto.CompletePasswordResetRequest;
import com.thanh.workhub.auth.internal.api.dto.PasswordResetCompletedResponse;
import com.thanh.workhub.auth.internal.api.dto.PasswordResetResponse;
import com.thanh.workhub.auth.internal.api.dto.PasswordResetVerifiedResponse;
import com.thanh.workhub.auth.internal.api.dto.VerifyOtpRequest;
import com.thanh.workhub.auth.internal.domain.PasswordResetRequest;
import com.thanh.workhub.auth.internal.domain.SessionRevokeReason;
import com.thanh.workhub.auth.internal.persistence.PasswordResetRequestRepository;
import com.thanh.workhub.platform.mail.EmailMessage;
import com.thanh.workhub.platform.mail.EmailSender;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

/**
 * Quên mật khẩu (chưa đăng nhập), khớp 4 màn của app:
 * <ol>
 *   <li>{@link #start}: nhập email hoặc số điện thoại → gửi mã 6 số tới email của tài khoản.</li>
 *   <li>{@link #verify}: nhập mã ("Kiểm tra email"), có {@link #resendOtp}. Đúng mã thì nhận {@code resetToken}.</li>
 *   <li>{@link #complete}: đặt mật khẩu mới bằng {@code resetToken}.</li>
 *   <li>Kết quả: mọi thiết bị bị đăng xuất, gửi email thông báo, client tắt Face ID và về màn đăng nhập.</li>
 * </ol>
 *
 * <p>Chống dò tài khoản: email/số điện thoại không tồn tại vẫn tạo yêu cầu và trả phản hồi y hệt (chỉ không
 * gửi mail, mã không bao giờ đúng). Mail gửi ở luồng nền sau khi commit để thời gian phản hồi cũng như nhau;
 * gửi lỗi thì người dùng bấm "Gửi lại mã".
 */
@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    /** Không gian khóa advisory riêng cho quên mật khẩu. */
    private static final int LOCK_NAMESPACE = 0x50575253; // "PWRS"
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Executor MAIL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final PasswordResetRequestRepository requests;
    private final LoginSessionService loginSessions;
    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final Clock clock;

    private final Duration requestTtl;
    private final Duration resetTokenTtl;
    private final Duration otpTtl;
    private final Duration resendCooldown;
    private final int maxOtpAttempts;
    private final int maxSendsPerRequest;
    private final int maxRequestsPerIdentifierPerHour;
    private final int maxRequestsPerIpPerHour;

    public PasswordResetService(PasswordResetRequestRepository requests, LoginSessionService loginSessions,
            UserApi userApi, PasswordEncoder passwordEncoder, EmailSender emailSender, Clock clock,
            @Value("${app.password-reset.request-ttl-minutes}") long requestTtlMinutes,
            @Value("${app.password-reset.reset-token-ttl-minutes}") long resetTokenTtlMinutes,
            @Value("${app.otp.ttl-minutes}") long otpTtlMinutes,
            @Value("${app.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
            @Value("${app.otp.max-attempts}") int maxOtpAttempts,
            @Value("${app.otp.max-sends}") int maxSendsPerRequest,
            @Value("${app.password-reset.max-requests-per-identifier-per-hour}") int maxRequestsPerIdentifierPerHour,
            @Value("${app.password-reset.max-requests-per-ip-per-hour}") int maxRequestsPerIpPerHour) {
        this.requests = requests;
        this.loginSessions = loginSessions;
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
        this.requestTtl = Duration.ofMinutes(requestTtlMinutes);
        this.resetTokenTtl = Duration.ofMinutes(resetTokenTtlMinutes);
        this.otpTtl = Duration.ofMinutes(otpTtlMinutes);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.maxOtpAttempts = maxOtpAttempts;
        this.maxSendsPerRequest = maxSendsPerRequest;
        this.maxRequestsPerIdentifierPerHour = maxRequestsPerIdentifierPerHour;
        this.maxRequestsPerIpPerHour = maxRequestsPerIpPerHour;
    }

    // ---- Màn 1: nhập email / số điện thoại, gửi mã ----

    @Transactional
    public PasswordResetResponse start(String rawIdentifier, String ipAddress) {
        var identifier = Identifier.parse(rawIdentifier);
        requests.lockIdentifier(LOCK_NAMESPACE, identifier.value().hashCode());

        Instant now = now();
        Instant hourAgo = now.minus(Duration.ofHours(1));
        // Áp dụng như nhau cho cả email/số chưa đăng ký, nên giới hạn này không làm lộ tài khoản.
        if (requests.countByIdentifierAndCreatedAtAfter(identifier.value(), hourAgo) >= maxRequestsPerIdentifierPerHour
                || (ipAddress != null
                        && requests.countByIpAddressAndCreatedAtAfter(ipAddress, hourAgo) >= maxRequestsPerIpPerHour)) {
            throw new ApiException(ErrorCode.OTP_SEND_LIMIT_REACHED,
                    "Bạn đã yêu cầu đặt lại mật khẩu quá nhiều lần, vui lòng thử lại sau 1 giờ");
        }

        Optional<UserCredentials> user = identifier.isEmail()
                ? userApi.findCredentialsByEmail(identifier.value())
                : userApi.findCredentialsByPhone(identifier.value());
        var reset = new PasswordResetRequest(identifier.value(), user.map(UserCredentials::id).orElse(null),
                user.map(UserCredentials::email).orElse(null), ipAddress, now, requestTtl);
        issueOtp(reset, now);
        requests.save(reset);
        return toResponse(reset, identifier);
    }

    @Transactional
    public PasswordResetResponse resendOtp(UUID resetId) {
        var reset = loadOpen(resetId);
        if (reset.getStatus() != PasswordResetRequest.Status.PENDING) {
            throw new ApiException(ErrorCode.DATA_CONFLICT, "Mã đã được xác minh, vui lòng đặt mật khẩu mới");
        }
        Instant now = now();
        var availableAt = reset.getOtpSentAt().plus(resendCooldown);
        if (now.isBefore(availableAt)) {
            long seconds = (Duration.between(now, availableAt).toMillis() + 999) / 1000; // làm tròn lên
            throw new ApiException(ErrorCode.OTP_RESEND_TOO_SOON,
                    "Vui lòng chờ " + seconds + " giây trước khi gửi lại mã");
        }
        if (reset.getOtpSendCount() >= maxSendsPerRequest) {
            throw new ApiException(ErrorCode.OTP_SEND_LIMIT_REACHED,
                    "Đã gửi mã quá nhiều lần, vui lòng thực hiện lại từ đầu");
        }
        issueOtp(reset, now);
        return toResponse(reset, Identifier.parse(reset.getIdentifier()));
    }

    // ---- Màn 2: nhập mã ----

    /** Lần nhập sai mã phải được lưu lại dù ném lỗi, nên không rollback khi gặp ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public PasswordResetVerifiedResponse verify(UUID resetId, VerifyOtpRequest request) {
        var reset = loadOpen(resetId);
        Instant now = now();
        if (reset.getOtpAttempts() >= maxOtpAttempts) throw new ApiException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        if (!now.isBefore(reset.getOtpExpiresAt())) throw new ApiException(ErrorCode.OTP_EXPIRED);
        if (!OtpCodes.matches(reset.getOtpHash(), reset.getId(), request.code())) {
            reset.otpFailed();
            int remaining = maxOtpAttempts - reset.getOtpAttempts();
            if (remaining <= 0) throw new ApiException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
            throw new ApiException(ErrorCode.OTP_INVALID, "Mã xác thực không đúng. Bạn còn " + remaining + " lần thử");
        }
        // Đúng mã (kể cả gọi lại lần hai khi client mất kết nối): cấp token mới, token cũ mất hiệu lực.
        String token = newResetToken();
        reset.verified(sha256(token), now, resetTokenTtl);
        return new PasswordResetVerifiedResponse(token, reset.getExpiresAt(), maskEmail(reset.getEmail()));
    }

    // ---- Màn 3: đặt mật khẩu mới ----

    @Transactional
    public PasswordResetCompletedResponse complete(UUID resetId, CompletePasswordResetRequest request) {
        var reset = loadOpen(resetId);
        if (reset.getStatus() != PasswordResetRequest.Status.VERIFIED) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_NOT_VERIFIED);
        }
        if (!MessageDigest.isEqual(reset.getResetTokenHash().getBytes(StandardCharsets.US_ASCII),
                sha256(request.resetToken()).getBytes(StandardCharsets.US_ASCII))) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        Long userId = reset.getUserId();
        var user = userApi.findCredentialsById(userId);
        // Email tài khoản đã đổi trong lúc chờ: mã được gửi tới email cũ, không dùng được nữa.
        if (user.isEmpty() || !user.get().email().equals(reset.getEmail())) {
            throw new ApiException(ErrorCode.PASSWORD_RESET_EXPIRED);
        }
        PasswordPolicy.validate(request.newPassword(), request.confirmPassword());
        if (passwordEncoder.matches(request.newPassword(), user.get().passwordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        userApi.changePassword(userId, passwordEncoder.encode(request.newPassword()));
        Instant now = now();
        reset.complete(now);
        // Các yêu cầu quên mật khẩu khác của tài khoản này (nếu có) không còn dùng được.
        requests.findOpenByUserId(userId).forEach(PasswordResetRequest::cancel);
        int revoked = loginSessions.revokeAll(userId, SessionRevokeReason.PASSWORD_RESET);

        sendAfterCommit(AuthEmails.passwordReset(reset.getEmail(), fullName(userId)));
        return new PasswordResetCompletedResponse(revoked, maskEmail(reset.getEmail()), true);
    }

    // ---- Hỗ trợ ----

    private PasswordResetRequest loadOpen(UUID resetId) {
        var reset = requests.findForUpdate(resetId)
                .orElseThrow(() -> new ApiException(ErrorCode.PASSWORD_RESET_NOT_FOUND));
        if (!reset.isOpen(now())) throw new ApiException(ErrorCode.PASSWORD_RESET_EXPIRED);
        return reset;
    }

    /** Tài khoản không tồn tại: hash của một chuỗi không phải 6 chữ số, nên không mã nào khớp và không gửi mail. */
    private void issueOtp(PasswordResetRequest reset, Instant now) {
        if (reset.getUserId() == null) {
            reset.otpIssued(OtpCodes.hash(reset.getId(), UUID.randomUUID().toString()), now, otpTtl);
            return;
        }
        String code = OtpCodes.generate();
        reset.otpIssued(OtpCodes.hash(reset.getId(), code), now, otpTtl);
        sendAfterCommit(AuthEmails.passwordResetOtp(reset.getEmail(), fullName(reset.getUserId()), code, otpTtl));
    }

    /** Gửi ở luồng nền sau khi commit: không làm chậm phản hồi, gửi lỗi chỉ ghi log. */
    private void sendAfterCommit(EmailMessage message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                MAIL_EXECUTOR.execute(() -> {
                    try {
                        emailSender.send(message);
                    } catch (RuntimeException exception) {
                        log.warn("Không gửi được email quên mật khẩu: {}", message, exception);
                    }
                });
            }
        });
    }

    private String fullName(Long userId) {
        return userApi.getProfile(userId).fullName();
    }

    private PasswordResetResponse toResponse(PasswordResetRequest reset, Identifier identifier) {
        return new PasswordResetResponse(reset.getId(),
                identifier.isEmail() ? "EMAIL" : "PHONE",
                identifier.isEmail() ? maskEmail(identifier.value()) : maskPhone(identifier.value()),
                reset.getOtpExpiresAt(),
                reset.getOtpSentAt().plus(resendCooldown),
                Math.max(0, maxOtpAttempts - reset.getOtpAttempts()),
                reset.getExpiresAt());
    }

    private Instant now() {
        return clock.instant();
    }

    private static String newResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** "minhanh@email.com" → "mi•••••@email.com". */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        int keep = local.length() <= 2 ? 1 : 2;
        return local.substring(0, keep) + "•".repeat(Math.max(1, local.length() - keep)) + email.substring(at);
    }

    /** "0901234567" → "090•••••67". */
    static String maskPhone(String phone) {
        return phone.substring(0, 3) + "•".repeat(phone.length() - 5) + phone.substring(phone.length() - 2);
    }

    /** Email (chữ thường) hoặc số điện thoại (bỏ khoảng trắng). */
    private record Identifier(String value, boolean isEmail) {
        static Identifier parse(String raw) {
            String value = raw == null ? "" : raw.strip();
            if (value.contains("@")) {
                value = value.toLowerCase(Locale.ROOT);
                if (EMAIL.matcher(value).matches()) return new Identifier(value, true);
            } else {
                value = value.replaceAll("\\s+", "");
                if (PHONE.matcher(value).matches()) return new Identifier(value, false);
            }
            throw new ApiException(ErrorCode.IDENTIFIER_INVALID);
        }
    }
}
