package com.thanh.workhub.user.internal.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT thay toàn bộ cài đặt học, mọi trường đều bắt buộc. */
public record LearningSettingsRequest(
        @NotNull(message = "Vui lòng chọn mục tiêu mỗi ngày")
        @Min(value = 5, message = "Mục tiêu mỗi ngày từ 5 đến 120 phút")
        @Max(value = 120, message = "Mục tiêu mỗi ngày từ 5 đến 120 phút")
        Integer dailyGoalMinutes,
        @NotNull(message = "Thiếu cài đặt nhắc học") @Valid Reminder reminder,
        @NotNull(message = "Vui lòng chọn kiểu chữ")
        @Pattern(regexp = "SIMPLIFIED|TRADITIONAL", message = "script phải là SIMPLIFIED hoặc TRADITIONAL")
        String script,
        @NotNull(message = "Vui lòng chọn cách hiện pinyin")
        @Pattern(regexp = "ALWAYS|ON_TAP|NEVER", message = "pinyinMode phải là ALWAYS, ON_TAP hoặc NEVER")
        String pinyinMode) {

    public record Reminder(
            @NotNull(message = "Thiếu reminder.enabled") Boolean enabled,
            @NotNull(message = "Vui lòng chọn giờ nhắc")
            @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Giờ nhắc có dạng HH:mm, ví dụ 20:00")
            String time,
            @NotNull(message = "Vui lòng chọn ngày nhắc") @Size(max = 7, message = "Tối đa 7 ngày")
            List<@NotNull @Pattern(regexp = "MON|TUE|WED|THU|FRI|SAT|SUN",
                    message = "Ngày nhắc phải là MON, TUE, WED, THU, FRI, SAT hoặc SUN") String> days,
            @NotNull(message = "Thiếu reminder.skipIfGoalMet") Boolean skipIfGoalMet,
            @NotBlank(message = "Vui lòng chọn múi giờ") @Size(max = 64) String timezone) {
    }
}
