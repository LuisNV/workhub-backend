
package com.thanh.workhub.auth.internal.application;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.persistence.LoginResponse;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.user.internal.domain.User;
import com.thanh.workhub.user.internal.persistence.UserRepository;


@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        // 1. Chuẩn hóa email
        String email = request.email()
            .trim()
            .toLowerCase(Locale.ROOT);

        // 2. Tìm người dùng trong PostgreSQL
        Optional<User> userOptional =
            userRepository.findByEmail(email);

        // 3. Nếu không tìm thấy -> từ chối
        if (userOptional.isEmpty()) {
            throw invalidCredentials();
        }

        User user = userOptional.get();

        // 4. Kiểm tra password với BCrypt hash
        boolean passwordValid = passwordEncoder.matches(
            request.password(),
            user.getPasswordHash()
        );

        if (!passwordValid) {
            throw invalidCredentials();
        }

        // 5. Phát JWT khi đăng nhập đúng
        String accessToken =
            jwtService.generateAccessToken(user);

        // 6. Trả kết quả cho client
        return new LoginResponse(
            accessToken,
            "Bearer",
            jwtService.getExpiresInSeconds()
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Email hoặc mật khẩu không đúng"
        );
    }
}
