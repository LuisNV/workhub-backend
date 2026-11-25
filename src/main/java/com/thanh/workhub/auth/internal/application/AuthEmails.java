package com.thanh.workhub.auth.internal.application;

import java.time.Duration;

import org.springframework.web.util.HtmlUtils;

import com.thanh.workhub.platform.mail.EmailMessage;

/** Nội dung email của module auth (đăng ký, đổi mật khẩu). */
final class AuthEmails {
    private AuthEmails() {
    }

    static EmailMessage registrationOtp(String to, String fullName, String code, Duration validFor) {
        return otp(to, "Mã xác thực đăng ký tài khoản", "Xác thực email của bạn", fullName,
                "nhập mã dưới đây để hoàn tất đăng ký tài khoản.",
                "Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.", code, validFor);
    }

    static EmailMessage passwordChangeOtp(String to, String fullName, String code, Duration validFor) {
        return otp(to, "Mã xác minh đổi mật khẩu", "Xác minh đổi mật khẩu", fullName,
                "nhập mã dưới đây để xác nhận đổi mật khẩu tài khoản của bạn.",
                "Không phải bạn yêu cầu đổi mật khẩu? Đừng chia sẻ mã này với bất kỳ ai "
                        + "và hãy đổi mật khẩu ngay vì có thể mật khẩu của bạn đã bị lộ.",
                code, validFor);
    }

    static EmailMessage passwordChanged(String to, String fullName, boolean otherDevicesLoggedOut) {
        String devices = otherDevicesLoggedOut
                ? "Các thiết bị khác đã được đăng xuất."
                : "Các thiết bị khác vẫn đang đăng nhập.";
        String text = """
                Xin chào %s,

                Mật khẩu tài khoản của bạn vừa được thay đổi. %s

                Nếu không phải bạn thực hiện, hãy dùng chức năng "Quên mật khẩu" để lấy lại tài khoản ngay.
                """.formatted(fullName, devices);
        String html = layout("Mật khẩu đã được thay đổi", """
                <p style="margin:0 0 12px;font-size:15px;line-height:1.5">Xin chào <b>%s</b>, mật khẩu tài khoản của bạn vừa được thay đổi. %s</p>
                <p style="margin:0;font-size:14px;color:#b4232c">Nếu không phải bạn thực hiện, hãy dùng chức năng "Quên mật khẩu" để lấy lại tài khoản ngay.</p>
                """.formatted(HtmlUtils.htmlEscape(fullName), devices));
        return new EmailMessage(to, "Mật khẩu của bạn đã được thay đổi", text, html);
    }

    private static EmailMessage otp(String to, String subject, String title, String fullName, String purpose,
            String warning, String code, Duration validFor) {
        long minutes = validFor.toMinutes();
        String text = """
                Xin chào %s,

                Mã xác minh của bạn là: %s
                Dùng mã này để %s

                Mã có hiệu lực trong %d phút. Không chia sẻ mã này với bất kỳ ai.
                %s
                """.formatted(fullName, code, purpose, minutes, warning);
        String html = layout(title, """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.5">Xin chào <b>%s</b>, %s</p>
                <div style="font-size:32px;font-weight:bold;letter-spacing:10px;text-align:center;
                            background:#eef0ff;color:#3b44d6;border-radius:12px;padding:16px 0;margin:0 0 20px">%s</div>
                <p style="margin:0 0 8px;font-size:14px;color:#5b6070">Mã có hiệu lực trong %d phút. Không chia sẻ mã này với bất kỳ ai.</p>
                <p style="margin:0;font-size:13px;color:#8a8fa0">%s</p>
                """.formatted(HtmlUtils.htmlEscape(fullName), purpose, code, minutes, warning));
        return new EmailMessage(to, subject, text, html);
    }

    private static String layout(String title, String body) {
        return """
                <!doctype html>
                <html lang="vi"><body style="margin:0;padding:24px;background:#f4f5fb;font-family:Arial,Helvetica,sans-serif;color:#1f2330">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center">
                      <table role="presentation" width="480" cellpadding="0" cellspacing="0"
                             style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px">
                        <tr><td>
                          <h1 style="margin:0 0 12px;font-size:22px">%s</h1>
                          %s
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(title, body);
    }
}
