package com.thanh.workhub.user.internal.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cài đặt học: mục tiêu mỗi ngày, nhắc học, kiểu chữ Hán và cách hiện pinyin. */
@Entity
@Table(name = "user_learning_settings")
public class LearningSettings {
    public enum Script { SIMPLIFIED, TRADITIONAL }
    /** Luôn hiện pinyin / chỉ hiện khi chạm vào chữ / không hiện. */
    public enum PinyinMode { ALWAYS, ON_TAP, NEVER }

    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "daily_goal_minutes", nullable = false)
    private int dailyGoalMinutes;
    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled;
    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;
    @Column(name = "reminder_days", nullable = false, length = 27)
    private String reminderDays;
    @Column(name = "skip_if_goal_met", nullable = false)
    private boolean skipIfGoalMet;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Script script;
    @Enumerated(EnumType.STRING)
    @Column(name = "pinyin_mode", nullable = false, length = 20)
    private PinyinMode pinyinMode;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LearningSettings() {
    }

    public LearningSettings(Long userId) {
        this.userId = userId;
    }

    public void update(int dailyGoalMinutes, boolean reminderEnabled, LocalTime reminderTime,
            Set<DayOfWeek> reminderDays, boolean skipIfGoalMet, String timezone, Script script,
            PinyinMode pinyinMode, Instant now) {
        this.dailyGoalMinutes = dailyGoalMinutes;
        this.reminderEnabled = reminderEnabled;
        this.reminderTime = reminderTime;
        // Bỏ trùng và lưu theo thứ tự Thứ Hai → Chủ Nhật.
        this.reminderDays = reminderDays.stream().distinct().sorted()
                .map(LearningSettings::code).collect(Collectors.joining(","));
        this.skipIfGoalMet = skipIfGoalMet;
        this.timezone = timezone;
        this.script = script;
        this.pinyinMode = pinyinMode;
        this.updatedAt = now;
    }

    /** DayOfWeek.MONDAY → "MON". */
    public static String code(DayOfWeek day) {
        return day.name().substring(0, 3);
    }

    /** "MON" → DayOfWeek.MONDAY. */
    public static DayOfWeek day(String code) {
        return Arrays.stream(DayOfWeek.values()).filter(day -> code(day).equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ngày không hợp lệ: " + code));
    }

    public Long getUserId() { return userId; }
    public int getDailyGoalMinutes() { return dailyGoalMinutes; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public LocalTime getReminderTime() { return reminderTime; }
    public String getReminderDays() { return reminderDays; }
    public boolean isSkipIfGoalMet() { return skipIfGoalMet; }
    public String getTimezone() { return timezone; }
    public Script getScript() { return script; }
    public PinyinMode getPinyinMode() { return pinyinMode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
