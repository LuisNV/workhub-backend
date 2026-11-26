package com.thanh.workhub.vocabulary.internal.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thanh.workhub.platform.security.CurrentUserId;
import com.thanh.workhub.vocabulary.internal.api.dto.ReviewRequest;
import com.thanh.workhub.vocabulary.internal.api.dto.ReviewResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.UpdateProgressRequest;
import com.thanh.workhub.vocabulary.internal.api.dto.UpdateProgressResponse;
import com.thanh.workhub.vocabulary.internal.application.HskProgressService;

import jakarta.validation.Valid;

/** Ghi tiến độ học từ HSK của người dùng đang đăng nhập. */
@RestController
@RequestMapping("/api/v1/hsk")
public class HskProgressController {
    private final HskProgressService progressService;

    public HskProgressController(HskProgressService progressService) {
        this.progressService = progressService;
    }

    /** Kết quả một lượt học thẻ ghi nhớ. */
    @PostMapping("/reviews")
    public ReviewResponse review(@CurrentUserId Long userId, @Valid @RequestBody ReviewRequest request) {
        return progressService.review(userId, request);
    }

    /** Tự đánh dấu "Đã thuộc", "Đang học" hoặc đặt lại "Chưa học". */
    @PutMapping("/levels/{level}/words/{entryNumber}/progress")
    public UpdateProgressResponse update(@CurrentUserId Long userId, @PathVariable int level,
            @PathVariable int entryNumber, @Valid @RequestBody UpdateProgressRequest request) {
        return progressService.update(userId, level, entryNumber, request.status());
    }
}
