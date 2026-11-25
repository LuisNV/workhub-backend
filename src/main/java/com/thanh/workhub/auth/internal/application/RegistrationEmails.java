package com.thanh.workhub.auth.internal.application;

import java.time.Duration;

import org.springframework.web.util.HtmlUtils;

import com.thanh.workhub.platform.mail.EmailMessage;

/** Nội dung email của luồng đăng ký. */
final class RegistrationEmails {
    private RegistrationEmails() {
    }

    static EmailMessage otp(String to, String fullName, String code, Duration validFor) {
        long minutes = validFor.toMinutes();
        String text = """
                Xin chào %s,

                Mã xác thực đăng ký tài khoản của bạn là: %s

                Mã có hiệu lực trong %d phút. Không chia sẻ mã này với bất kỳ ai.
                Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.
                """.formatted(fullName, code, minutes);

        String name = HtmlUtils.htmlEscape(fullName);
        String html = """
                <!doctype html>
                <html lang="vi"><body style="margin:0;padding:24px;background:#f4f5fb;font-family:Arial,Helvetica,sans-serif;color:#1f2330">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center">
                      <table role="presentation" width="480" cellpadding="0" cellspacing="0"
                             style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px">
                        <tr><td>
                          <h1 style="margin:0 0 12px;font-size:22px">Xác thực email của bạn</h1>
                          <p style="margin:0 0 20px;font-size:15px;line-height:1.5">Xin chào <b>%s</b>, nhập mã dưới đây để hoàn tất đăng ký tài khoản.</p>
                          <div style="font-size:32px;font-weight:bold;letter-spacing:10px;text-align:center;
                                      background:#eef0ff;color:#3b44d6;border-radius:12px;padding:16px 0;margin:0 0 20px">%s</div>
                          <p style="margin:0 0 8px;font-size:14px;color:#5b6070">Mã có hiệu lực trong %d phút. Không chia sẻ mã này với bất kỳ ai.</p>
                          <p style="margin:0;font-size:13px;color:#8a8fa0">Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(name, code, minutes);

        return new EmailMessage(to, "Mã xác thực đăng ký tài khoản", text, html);
    }
}
