package com.thanh.workhub.platform.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

/**
 * Dùng khi {@code app.mail.enabled=false}. Mặc định từ chối gửi (MAIL_DISABLED). Riêng máy cá nhân
 * bật {@code app.mail.log-only=true} để in email ra console, test đăng ký không cần Gmail.
 */
public class DisabledEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(DisabledEmailSender.class);

    private final boolean logOnly;

    public DisabledEmailSender(boolean logOnly) {
        this.logOnly = logOnly;
    }

    @Override
    public void send(EmailMessage message) {
        if (!logOnly) throw new ApiException(ErrorCode.MAIL_DISABLED);
        log.warn("[MAIL LOG-ONLY] Không gửi thật. To: {} | Subject: {}\n{}",
                message.to(), message.subject(), message.textBody());
    }
}
