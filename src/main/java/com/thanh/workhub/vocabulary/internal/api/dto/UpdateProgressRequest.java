package com.thanh.workhub.vocabulary.internal.api.dto;

import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

import jakarta.validation.constraints.NotNull;

/** Tự đánh dấu trạng thái một từ: MASTERED (đã thuộc), LEARNING (học lại), NOT_STARTED (xóa tiến độ). */
public record UpdateProgressRequest(
        @NotNull(message = "Thiếu status (NOT_STARTED, LEARNING hoặc MASTERED)") WordStatus status) {
}
