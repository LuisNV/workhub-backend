package com.thanh.workhub.vocabulary.internal.api.dto;

import java.util.List;

/** Một trang danh sách từ. {@code totalItems} là số từ của bộ lọc đang chọn ("24 từ"). */
public record HskWordPageResponse(
        List<HskWordResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
