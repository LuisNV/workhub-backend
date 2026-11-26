package com.thanh.workhub.user.internal.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.platform.security.CurrentUserId;
import com.thanh.workhub.user.internal.api.dto.LearningSettingsRequest;
import com.thanh.workhub.user.internal.api.dto.LearningSettingsResponse;
import com.thanh.workhub.user.internal.application.LearningSettingsService;

import jakarta.validation.Valid;

/** Cài đặt học của người dùng đang đăng nhập. */
@RestController
@RequestMapping("/api/v1/users/me/learning-settings")
public class LearningSettingsController {
    private final LearningSettingsService learningSettingsService;

    public LearningSettingsController(LearningSettingsService learningSettingsService) {
        this.learningSettingsService = learningSettingsService;
    }

    /** Chưa lưu lần nào thì trả giá trị mặc định ({@code updatedAt = null}). */
    @GetMapping
    public LearningSettingsResponse get(@CurrentUserId Long userId) {
        return learningSettingsService.get(userId);
    }

    /** Thay toàn bộ cài đặt. */
    @PutMapping
    public LearningSettingsResponse update(@CurrentUserId Long userId,
            @Valid @RequestBody LearningSettingsRequest request) {
        return learningSettingsService.update(userId, request);
    }
}
