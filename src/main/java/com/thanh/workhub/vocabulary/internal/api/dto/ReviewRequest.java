package com.thanh.workhub.vocabulary.internal.api.dto;

import java.util.List;

import com.thanh.workhub.vocabulary.internal.domain.ReviewResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Kết quả một lượt học thẻ ghi nhớ, gửi một lần khi kết thúc lượt. Thứ tự trong danh sách là thứ tự ôn. */
public record ReviewRequest(
        @NotEmpty(message = "Vui lòng gửi ít nhất 1 thẻ")
        @Size(max = 100, message = "Mỗi lần gửi tối đa 100 thẻ")
        List<@Valid @NotNull Item> items) {

    public record Item(
            @NotNull(message = "Thiếu level") @Min(value = 1, message = "Cấp HSK phải từ 1 đến 9")
            @Max(value = 9, message = "Cấp HSK phải từ 1 đến 9") Integer level,
            @NotNull(message = "Thiếu entryNumber") @Positive(message = "entryNumber phải lớn hơn 0")
            Integer entryNumber,
            @NotNull(message = "Thiếu result (REMEMBERED hoặc FORGOT)") ReviewResult result) {
    }
}
