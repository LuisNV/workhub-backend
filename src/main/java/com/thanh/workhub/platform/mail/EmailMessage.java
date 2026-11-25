package com.thanh.workhub.platform.mail;

/** Một email cần gửi. {@code htmlBody} có thể null, khi đó chỉ gửi bản text. */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {

    @Override
    public String toString() {
        // Nội dung có thể chứa mã OTP, không được in ra log.
        return "EmailMessage[to=" + to + ", subject=" + subject + "]";
    }
}
