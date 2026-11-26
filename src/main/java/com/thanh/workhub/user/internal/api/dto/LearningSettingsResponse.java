package com.thanh.workhub.user.internal.api.dto;

import java.time.Instant;
import java.util.List;

/** Cài đặt học. {@code updatedAt = null}: người dùng chưa lưu lần nào, đang dùng giá trị mặc định. */
public record LearningSettingsResponse(
        int dailyGoalMinutes,
        Reminder reminder,
        String script,
        String pinyinMode,
        Instant updatedAt) {

    public record Reminder(boolean enabled, String time, List<String> days, boolean skipIfGoalMet, String timezone) {
    }
}
