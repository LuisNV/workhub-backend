package com.thanh.workhub.auth.internal.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.auth.internal.api.dto.LoginSessionResponse;
import com.thanh.workhub.auth.internal.application.LoginSessionService;
import com.thanh.workhub.platform.security.CurrentUserId;

/** Quản lý thiết bị đăng nhập của chính user đang gọi. */
@RestController
@RequestMapping("/api/v1/auth/sessions")
public class SessionController {
    private final LoginSessionService loginSessions;

    public SessionController(LoginSessionService loginSessions) {
        this.loginSessions = loginSessions;
    }

    @GetMapping
    public List<LoginSessionResponse> list(@CurrentUserId Long userId, @AuthenticationPrincipal Jwt jwt) {
        return loginSessions.list(userId, CurrentSession.id(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@CurrentUserId Long userId, @PathVariable UUID id) {
        loginSessions.revoke(userId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke-others")
    public Map<String, Integer> revokeOthers(@CurrentUserId Long userId, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("revokedCount", loginSessions.revokeOthers(userId, CurrentSession.id(jwt)));
    }
}
