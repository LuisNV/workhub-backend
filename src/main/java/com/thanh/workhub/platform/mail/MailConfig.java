package com.thanh.workhub.platform.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.Assert;

@Configuration
public class MailConfig {

    @Bean
    public EmailSender emailSender(
            @Value("${app.mail.enabled}") boolean enabled,
            @Value("${app.mail.log-only:false}") boolean logOnly,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.from-name}") String fromName,
            @Value("${spring.mail.password:}") String password,
            ObjectProvider<JavaMailSender> mailSender) {
        if (!enabled) return new DisabledEmailSender(logOnly);
        Assert.hasText(from, "MAIL_USERNAME (hoặc MAIL_FROM) phải được cấu hình khi MAIL_ENABLED=true");
        Assert.hasText(password, "MAIL_PASSWORD (Gmail App Password) phải được cấu hình khi MAIL_ENABLED=true");
        return new SmtpEmailSender(mailSender.getObject(), from, fromName);
    }
}
