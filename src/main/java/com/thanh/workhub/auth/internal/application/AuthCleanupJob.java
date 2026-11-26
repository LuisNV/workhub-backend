package com.thanh.workhub.auth.internal.application;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.persistence.PasswordChangeRequestRepository;
import com.thanh.workhub.auth.internal.persistence.PasswordResetRequestRepository;
import com.thanh.workhub.auth.internal.persistence.RegistrationOtpSendRepository;
import com.thanh.workhub.auth.internal.persistence.RegistrationSessionRepository;

/**
 * Mỗi giờ xóa dữ liệu tạm đã hết hạn quá 1 ngày (giữ lại 1 ngày để client nhận lỗi "hết hạn" thay vì
 * "không tìm thấy"): phiên đăng ký, nhật ký gửi mã, yêu cầu đổi / đặt lại mật khẩu.
 */
@Component
class AuthCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

    private final RegistrationSessionRepository sessions;
    private final RegistrationOtpSendRepository otpSends;
    private final PasswordChangeRequestRepository passwordChanges;
    private final PasswordResetRequestRepository passwordResets;
    private final Clock clock;

    AuthCleanupJob(RegistrationSessionRepository sessions, RegistrationOtpSendRepository otpSends,
            PasswordChangeRequestRepository passwordChanges, PasswordResetRequestRepository passwordResets,
            Clock clock) {
        this.sessions = sessions;
        this.otpSends = otpSends;
        this.passwordChanges = passwordChanges;
        this.passwordResets = passwordResets;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purge() {
        var cutoff = clock.instant().minus(Duration.ofDays(1));
        int deletedSessions = sessions.deleteExpiredBefore(cutoff);
        int deletedSends = otpSends.deleteSentBefore(cutoff);
        int deletedChanges = passwordChanges.deleteExpiredBefore(cutoff);
        int deletedResets = passwordResets.deleteExpiredBefore(cutoff);
        if (deletedSessions + deletedSends + deletedChanges + deletedResets > 0) {
            log.info("Dọn dẹp: {} phiên đăng ký, {} bản ghi gửi mã, {} yêu cầu đổi mật khẩu, {} yêu cầu đặt lại mật khẩu",
                    deletedSessions, deletedSends, deletedChanges, deletedResets);
        }
    }
}
