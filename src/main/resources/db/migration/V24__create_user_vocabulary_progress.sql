-- Tiến độ học từng từ HSK của người dùng. Chưa có dòng nào nghĩa là "Chưa học" (NOT_STARTED).
-- Các cột ôn tập (streak, interval, due_at) do màn Học thẻ ghi nhớ / Kiểm tra cập nhật.
CREATE TABLE user_vocabulary_progress (
    user_id          BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    syllabus         varchar(16) NOT NULL,
    level            smallint    NOT NULL,
    entry_number     integer     NOT NULL,
    status           varchar(16) NOT NULL,
    correct_streak   smallint    NOT NULL DEFAULT 0,
    interval_days    smallint    NOT NULL DEFAULT 0,
    due_at           timestamptz NOT NULL,
    last_reviewed_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, syllabus, level, entry_number),
    FOREIGN KEY (syllabus, level, entry_number) REFERENCES hsk_vocabulary ON DELETE CASCADE,
    CONSTRAINT ck_vocabulary_progress_status CHECK (status IN ('LEARNING', 'MASTERED')),
    CONSTRAINT ck_vocabulary_progress_streak CHECK (correct_streak >= 0),
    CONSTRAINT ck_vocabulary_progress_interval CHECK (interval_days >= 0)
);

CREATE INDEX ix_vocabulary_progress_due ON user_vocabulary_progress (user_id, syllabus, level, due_at);
