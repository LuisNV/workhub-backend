package com.thanh.workhub.platform.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

import jakarta.mail.MessagingException;

/** Gửi qua SMTP (mặc định Gmail, cấu hình ở {@code spring.mail.*}). */
public class SmtpEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender, String fromAddress, String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, message.htmlBody() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (message.htmlBody() != null) {
                helper.setText(message.textBody(), message.htmlBody());
            } else {
                helper.setText(message.textBody());
            }
            mailSender.send(mime);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            // Không trả lỗi SMTP (có thể chứa tên đăng nhập) cho client.
            log.error("Gửi email thất bại: {}", message, exception);
            throw new ApiException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
