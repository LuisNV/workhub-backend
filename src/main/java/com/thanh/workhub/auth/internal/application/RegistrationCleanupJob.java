package com.thanh.workhub.auth.internal.application;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.auth.internal.persistence.RegistrationOtpSendRepository;
import com.thanh.workhub.auth.internal.persistence.RegistrationSessionRepository;

/**
 * Xóa phiên đăng ký đã hết hạn quá 1 ngày (giữ lại 1 ngày để client nhận lỗi "hết hạn" thay vì
 * "không tìm thấy") và nhật ký gửi mã cũ hơn 1 ngày.
 */
@Component
class RegistrationCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(RegistrationCleanupJob.class);

    private final RegistrationSessionRepository sessions;
    private final RegistrationOtpSendRepository otpSends;
    private final Clock clock;

    RegistrationCleanupJob(RegistrationSessionRepository sessions, RegistrationOtpSendRepository otpSends,
            Clock clock) {
        this.sessions = sessions;
        this.otpSends = otpSends;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purge() {
        var cutoff = clock.instant().minus(Duration.ofDays(1));
        int deletedSessions = sessions.deleteExpiredBefore(cutoff);
        int deletedSends = otpSends.deleteSentBefore(cutoff);
        if (deletedSessions + deletedSends > 0) {
            log.info("Đã xóa {} phiên đăng ký hết hạn, {} bản ghi gửi mã", deletedSessions, deletedSends);
        }
    }
}
