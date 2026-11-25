package com.thanh.workhub.platform.mail;

/**
 * Gửi email đồng bộ. Ném {@link com.thanh.workhub.platform.web.ApiException} với
 * {@code MAIL_SEND_FAILED} hoặc {@code MAIL_DISABLED} khi không gửi được, để nơi gọi rollback.
 */
public interface EmailSender {
    void send(EmailMessage message);
}
