-- Cài đặt học của người dùng. Chưa có dòng nào thì API trả giá trị mặc định.
CREATE TABLE user_learning_settings (
    user_id             BIGINT PRIMARY KEY REFERENCES app_users (id) ON DELETE CASCADE,
    daily_goal_minutes  INT          NOT NULL,
    reminder_enabled    BOOLEAN      NOT NULL,
    reminder_time       TIME         NOT NULL,
    -- Các ngày nhắc, cách nhau bởi dấu phẩy theo thứ tự trong tuần: "MON,TUE,WED".
    reminder_days       VARCHAR(27)  NOT NULL,
    skip_if_goal_met    BOOLEAN      NOT NULL,
    timezone            VARCHAR(64)  NOT NULL,
    script              VARCHAR(20)  NOT NULL,
    pinyin_mode         VARCHAR(20)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_learning_settings_goal CHECK (daily_goal_minutes BETWEEN 5 AND 120),
    CONSTRAINT ck_learning_settings_script CHECK (script IN ('SIMPLIFIED', 'TRADITIONAL')),
    CONSTRAINT ck_learning_settings_pinyin CHECK (pinyin_mode IN ('ALWAYS', 'ON_TAP', 'NEVER'))
);
