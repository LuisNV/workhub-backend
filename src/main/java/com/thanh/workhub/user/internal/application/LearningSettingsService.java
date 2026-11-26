package com.thanh.workhub.user.internal.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.internal.api.dto.LearningSettingsRequest;
import com.thanh.workhub.user.internal.api.dto.LearningSettingsResponse;
import com.thanh.workhub.user.internal.domain.LearningSettings;
import com.thanh.workhub.user.internal.persistence.LearningSettingsRepository;
import com.thanh.workhub.user.internal.persistence.UserRepository;

@Service
public class LearningSettingsService {
    /** Trả về khi người dùng chưa lưu cài đặt lần nào. */
    static final LearningSettingsResponse DEFAULTS = new LearningSettingsResponse(15,
            new LearningSettingsResponse.Reminder(false, "20:00",
                    List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), true, "Asia/Ho_Chi_Minh"),
            "SIMPLIFIED", "ALWAYS", null);

    private final LearningSettingsRepository settings;
    private final UserRepository users;
    private final Clock clock;

    public LearningSettingsService(LearningSettingsRepository settings, UserRepository users, Clock clock) {
        this.settings = settings;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LearningSettingsResponse get(Long userId) {
        ensureUserExists(userId);
        return settings.findById(userId).map(LearningSettingsService::toResponse).orElse(DEFAULTS);
    }

    @Transactional
    public LearningSettingsResponse update(Long userId, LearningSettingsRequest request) {
        ensureUserExists(userId);
        var reminder = request.reminder();
        String timezone = reminder.timezone().strip();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Múi giờ không hợp lệ, ví dụ: Asia/Ho_Chi_Minh");
        }
        var days = EnumSet.noneOf(DayOfWeek.class);
        reminder.days().forEach(code -> days.add(LearningSettings.day(code)));
        if (reminder.enabled() && days.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Vui lòng chọn ít nhất 1 ngày nhắc học");
        }

        var entity = settings.findById(userId).orElseGet(() -> new LearningSettings(userId));
        entity.update(request.dailyGoalMinutes(), reminder.enabled(), LocalTime.parse(reminder.time()), days,
                reminder.skipIfGoalMet(), timezone, LearningSettings.Script.valueOf(request.script()),
                LearningSettings.PinyinMode.valueOf(request.pinyinMode()), clock.instant());
        return toResponse(settings.save(entity));
    }

    private void ensureUserExists(Long userId) {
        if (!users.existsById(userId)) throw new UserNotFoundException(userId);
    }

    private static LearningSettingsResponse toResponse(LearningSettings s) {
        List<String> days = s.getReminderDays().isEmpty() ? List.of() : Arrays.asList(s.getReminderDays().split(","));
        return new LearningSettingsResponse(s.getDailyGoalMinutes(),
                new LearningSettingsResponse.Reminder(s.isReminderEnabled(), s.getReminderTime().toString(), days,
                        s.isSkipIfGoalMet(), s.getTimezone()),
                s.getScript().name(), s.getPinyinMode().name(), s.getUpdatedAt());
    }
}
