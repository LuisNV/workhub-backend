
package com.thanh.workhub.auth.internal.application;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.thanh.workhub.auth.internal.api.dto.LoginRequest;
import com.thanh.workhub.auth.internal.api.dto.LoginResponse;
import com.thanh.workhub.platform.security.JwtService;
import com.thanh.workhub.user.internal.domain.User;
import com.thanh.workhub.user.internal.persistence.UserRepository;

@Service

public class AuthService {

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponse login(LoginRequest request) {

        String email = request.email()
            .trim()
            .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
            .orElseThrow(this::invalidCredentials);

        boolean valid = passwordEncoder.matches(
            request.password(),
            user.getPasswordHash()
        );

        if (!valid) {
            throw invalidCredentials();
        }

        String accessToken =
            jwtService.generateAccessToken(user);

        String refreshToken =
            refreshTokenService.create(user.getId());

        return new LoginResponse(
            accessToken,
            refreshToken,
            "Bearer",
            jwtService.getExpiresInSeconds()
        );
    } 
    public User getProfile(String email) {
      
        var user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy người dùng với email " + email
            ));
       
        return user;
    }
    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Email hoặc mật khẩu không đúng"
        );
    }
}
