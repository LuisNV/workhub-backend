package com.thanh.workhub.auth.internal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.thanh.workhub.auth.internal.api.dto.PasswordChangeResponse;
import com.thanh.workhub.auth.internal.api.dto.PasswordChangedResponse;
import com.thanh.workhub.auth.internal.api.dto.StartPasswordChangeRequest;
import com.thanh.workhub.auth.internal.api.dto.VerifyOtpRequest;
import com.thanh.workhub.auth.internal.domain.PasswordChangeRequest;
import com.thanh.workhub.auth.internal.persistence.LoginSessionRepository;
import com.thanh.workhub.auth.internal.persistence.PasswordChangeRequestRepository;
import com.thanh.workhub.platform.mail.EmailMessage;
import com.thanh.workhub.platform.mail.EmailSender;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;

/**
 * Đổi mật khẩu 2 bước:
 * <ol>
 *   <li>{@link #start}: kiểm tra mật khẩu hiện tại (giới hạn số lần sai) và mật khẩu mới, gửi mã về email.</li>
 *   <li>{@link #verify}: nhập đúng mã thì áp dụng mật khẩu mới; tùy chọn đăng xuất các thiết bị khác.
 *       Thiết bị đang dùng vẫn đăng nhập.</li>
 * </ol>
 */
@Service
public class PasswordChangeService {
    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final PasswordChangeRequestRepository requests;
    private final LoginSessionRepository sessionRepository;
    private final LoginSessionService loginSessions;
    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final Clock clock;

    private final Duration requestTtl;
    private final Duration otpTtl;
    private final Duration resendCooldown;
    private final int maxOtpAttempts;
    private final int maxSendsPerRequest;
    private final int maxRequestsPerHour;

    public PasswordChangeService(PasswordChangeRequestRepository requests, LoginSessionRepository sessionRepository,
            LoginSessionService loginSessions, UserApi userApi,
            PasswordEncoder passwordEncoder, EmailSender emailSender, Clock clock,
            @Value("${app.password-change.request-ttl-minutes}") long requestTtlMinutes,
            @Value("${app.otp.ttl-minutes}") long otpTtlMinutes,
            @Value("${app.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
            @Value("${app.otp.max-attempts}") int maxOtpAttempts,
            @Value("${app.otp.max-sends}") int maxSendsPerRequest,
            @Value("${app.password-change.max-requests-per-hour}") int maxRequestsPerHour) {
        this.requests = requests;
        this.sessionRepository = sessionRepository;
        this.loginSessions = loginSessions;
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
        this.requestTtl = Duration.ofMinutes(requestTtlMinutes);
        this.otpTtl = Duration.ofMinutes(otpTtlMinutes);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.maxOtpAttempts = maxOtpAttempts;
        this.maxSendsPerRequest = maxSendsPerRequest;
        this.maxRequestsPerHour = maxRequestsPerHour;
    }

    // ---- Bước 1: kiểm tra mật khẩu, gửi mã ----

    @Transactional
    public PasswordChangeResponse start(Long userId, UUID currentSessionId, StartPasswordChangeRequest request) {
        // Khóa theo user: hai request song song không cùng vượt qua giới hạn số yêu cầu mỗi giờ.
        sessionRepository.lockUser(userId);
        UserCredentials user = credentials(userId);

        userApi.verifyCurrentPassword(userId, request.currentPassword());

        PasswordPolicy.validate(request.newPassword(), request.confirmPassword());
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }
        if (request.logoutOtherDevices() && currentSessionId == null) {
            throw new ApiException(ErrorCode.SESSION_UNKNOWN);
        }

        Instant now = now();
        if (requests.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(1))) >= maxRequestsPerHour) {
            throw new ApiException(ErrorCode.OTP_SEND_LIMIT_REACHED,
                    "Bạn đã yêu cầu đổi mật khẩu quá nhiều lần, vui lòng thử lại sau 1 giờ");
        }
        // Chỉ một yêu cầu đang chờ: yêu cầu mới thay yêu cầu cũ (mã cũ mất hiệu lực).
        requests.findPendingByUserId(userId).forEach(PasswordChangeRequest::cancel);

        var change = new PasswordChangeRequest(userId, user.email(), passwordEncoder.encode(request.newPassword()),
                user.passwordHash(), request.logoutOtherDevices(), currentSessionId, now, requestTtl);
        sendOtp(change, now);
        requests.save(change);
        return toResponse(change);
    }

    @Transactional
    public PasswordChangeResponse resendOtp(Long userId, UUID requestId) {
        var change = loadPending(userId, requestId);
        Instant now = now();
        var availableAt = change.getOtpSentAt().plus(resendCooldown);
        if (now.isBefore(availableAt)) {
            long seconds = (Duration.between(now, availableAt).toMillis() + 999) / 1000;
            throw new ApiException(ErrorCode.OTP_RESEND_TOO_SOON,
                    "Vui lòng chờ " + seconds + " giây trước khi gửi lại mã");
        }
        if (change.getOtpSendCount() >= maxSendsPerRequest) throw new ApiException(ErrorCode.OTP_SEND_LIMIT_REACHED);
        sendOtp(change, now);
        return toResponse(change);
    }

    // ---- Bước 2: xác minh mã, áp dụng mật khẩu mới ----

    /** Lần nhập sai mã phải được lưu lại dù ném lỗi, nên không rollback khi gặp ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public PasswordChangedResponse verify(Long userId, UUID requestId, VerifyOtpRequest request) {
        var change = loadPending(userId, requestId);
        Instant now = now();
        if (change.getOtpAttempts() >= maxOtpAttempts) throw new ApiException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        if (!now.isBefore(change.getOtpExpiresAt())) throw new ApiException(ErrorCode.OTP_EXPIRED);
        if (!OtpCodes.matches(change.getOtpHash(), change.getId(), request.code())) {
            change.otpFailed();
            int remaining = maxOtpAttempts - change.getOtpAttempts();
            if (remaining <= 0) throw new ApiException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
            throw new ApiException(ErrorCode.OTP_INVALID, "Mã xác thực không đúng. Bạn còn " + remaining + " lần thử");
        }

        UserCredentials user = credentials(userId);
        // Mật khẩu đã bị đổi bằng cách khác trong lúc chờ: không ghi đè.
        if (!user.passwordHash().equals(change.getCurrentPasswordHash())) {
            change.cancel();
            throw new ApiException(ErrorCode.PASSWORD_CHANGE_EXPIRED);
        }

        userApi.changePassword(userId, change.getNewPasswordHash());
        change.complete(now);
        int revoked = change.isLogoutOtherDevices()
                ? loginSessions.revokeOthers(userId, change.getSessionId())
                : 0;
        notifyAfterCommit(AuthEmails.passwordChanged(user.email(), fullName(userId), change.isLogoutOtherDevices()));
        return new PasswordChangedResponse(change.isLogoutOtherDevices(), revoked);
    }

    // ---- Hỗ trợ ----

    private PasswordChangeRequest loadPending(Long userId, UUID requestId) {
        var change = requests.findForUpdate(requestId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.PASSWORD_CHANGE_NOT_FOUND));
        if (!change.isPending(now())) throw new ApiException(ErrorCode.PASSWORD_CHANGE_EXPIRED);
        return change;
    }

    /** Gửi trong transaction: gửi lỗi thì rollback, yêu cầu/mã mới không được ghi nhận. */
    private void sendOtp(PasswordChangeRequest change, Instant now) {
        String code = OtpCodes.generate();
        change.otpIssued(OtpCodes.hash(change.getId(), code), now, otpTtl);
        emailSender.send(AuthEmails.passwordChangeOtp(change.getEmail(), fullName(change.getUserId()), code, otpTtl));
    }

    /** Email thông báo chỉ gửi sau khi đổi thành công; gửi lỗi không làm hỏng việc đổi mật khẩu. */
    private void notifyAfterCommit(EmailMessage message) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailSender.send(message);
                } catch (RuntimeException exception) {
                    log.warn("Không gửi được email báo đổi mật khẩu: {}", message, exception);
                }
            }
        });
    }

    private UserCredentials credentials(Long userId) {
        return userApi.findCredentialsById(userId).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private String fullName(Long userId) {
        return userApi.getProfile(userId).fullName();
    }

    private PasswordChangeResponse toResponse(PasswordChangeRequest change) {
        return new PasswordChangeResponse(change.getId(), change.getEmail(), change.isLogoutOtherDevices(),
                change.getOtpExpiresAt(), change.getOtpSentAt().plus(resendCooldown),
                Math.max(0, maxOtpAttempts - change.getOtpAttempts()), change.getExpiresAt());
    }

    private Instant now() {
        return clock.instant();
    }
}
