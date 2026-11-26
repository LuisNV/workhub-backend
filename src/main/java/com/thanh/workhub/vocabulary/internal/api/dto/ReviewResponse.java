package com.thanh.workhub.vocabulary.internal.api.dto;

import java.util.List;

/**
 * Trạng thái cuối của mỗi từ đã ôn (mỗi từ một lần, theo thứ tự xuất hiện đầu tiên) và số đếm mới của các cấp
 * liên quan, để app cập nhật thanh tiến độ mà không cần gọi lại {@code GET /hsk/levels}.
 */
public record ReviewResponse(List<WordProgressResponse> items, List<HskLevelResponse> levels) {
}
