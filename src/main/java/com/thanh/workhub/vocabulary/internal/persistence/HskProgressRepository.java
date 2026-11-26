package com.thanh.workhub.vocabulary.internal.persistence;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.thanh.workhub.vocabulary.internal.domain.WordProgress;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/** Đọc/ghi {@code user_vocabulary_progress}. Gọi trong transaction. */
@Repository
public class HskProgressRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public HskProgressRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Postgres advisory lock theo user, tự nhả khi transaction kết thúc. */
    public void lockUser(int namespace, long userId) {
        jdbc.query("select pg_advisory_xact_lock(:namespace, :key)",
                new MapSqlParameterSource("namespace", namespace).addValue("key", Long.hashCode(userId)),
                rs -> null);
    }

    /** Các từ trong {@code words} có trong từ điển. */
    public Set<WordKey> existingWords(String syllabus, Collection<WordKey> words) {
        String sql = """
                SELECT level, entry_number FROM hsk_vocabulary
                WHERE syllabus = :syllabus AND (level, entry_number) IN (:words)
                """;
        var params = new MapSqlParameterSource("syllabus", syllabus).addValue("words", tuples(words));
        return new HashSet<>(jdbc.query(sql, params,
                (rs, i) -> new WordKey(rs.getInt("level"), rs.getInt("entry_number"))));
    }

    /** Tiến độ hiện có của các từ; từ chưa học không có trong map. */
    public Map<WordKey, WordProgress> find(long userId, String syllabus, Collection<WordKey> words) {
        String sql = """
                SELECT level, entry_number, status, correct_streak, interval_days, due_at, last_reviewed_at
                FROM user_vocabulary_progress
                WHERE user_id = :userId AND syllabus = :syllabus AND (level, entry_number) IN (:words)
                """;
        var params = new MapSqlParameterSource("userId", userId).addValue("syllabus", syllabus)
                .addValue("words", tuples(words));
        var result = new HashMap<WordKey, WordProgress>();
        jdbc.query(sql, params, rs -> {
            result.put(new WordKey(rs.getInt("level"), rs.getInt("entry_number")),
                    new WordProgress(WordStatus.valueOf(rs.getString("status")), rs.getInt("correct_streak"),
                            rs.getInt("interval_days"), rs.getTimestamp("due_at").toInstant(),
                            rs.getTimestamp("last_reviewed_at").toInstant()));
        });
        return result;
    }

    public void save(long userId, String syllabus, WordKey word, WordProgress progress) {
        String sql = """
                INSERT INTO user_vocabulary_progress
                    (user_id, syllabus, level, entry_number, status, correct_streak, interval_days,
                     due_at, last_reviewed_at)
                VALUES (:userId, :syllabus, :level, :entryNumber, :status, :streak, :interval, :dueAt, :reviewedAt)
                ON CONFLICT (user_id, syllabus, level, entry_number) DO UPDATE
                SET status = EXCLUDED.status, correct_streak = EXCLUDED.correct_streak,
                    interval_days = EXCLUDED.interval_days, due_at = EXCLUDED.due_at,
                    last_reviewed_at = EXCLUDED.last_reviewed_at
                """;
        jdbc.update(sql, key(userId, syllabus, word)
                .addValue("status", progress.status().name())
                .addValue("streak", progress.correctStreak())
                .addValue("interval", progress.intervalDays())
                .addValue("dueAt", Timestamp.from(progress.dueAt()))
                .addValue("reviewedAt", Timestamp.from(progress.lastReviewedAt())));
    }

    public void delete(long userId, String syllabus, WordKey word) {
        jdbc.update("""
                DELETE FROM user_vocabulary_progress
                WHERE user_id = :userId AND syllabus = :syllabus AND level = :level AND entry_number = :entryNumber
                """, key(userId, syllabus, word));
    }

    private static MapSqlParameterSource key(long userId, String syllabus, WordKey word) {
        return new MapSqlParameterSource("userId", userId).addValue("syllabus", syllabus)
                .addValue("level", word.level()).addValue("entryNumber", word.entryNumber());
    }

    private static List<Object[]> tuples(Collection<WordKey> words) {
        return words.stream().map(w -> new Object[] {w.level(), w.entryNumber()}).toList();
    }

    public record WordKey(int level, int entryNumber) {
    }
}
