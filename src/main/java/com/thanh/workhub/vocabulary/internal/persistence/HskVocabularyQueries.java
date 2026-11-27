package com.thanh.workhub.vocabulary.internal.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.thanh.workhub.vocabulary.internal.api.dto.TonePracticeQuestion;
import com.thanh.workhub.vocabulary.internal.api.dto.DailyWordResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.FlashcardResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordDetailResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordResponse;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;

/**
 * Truy vấn đọc cho màn Từ vựng HSK. Từ điển (khóa ghép syllabus, level, entry_number) LEFT JOIN tiến độ của
 * người dùng: không có dòng tiến độ nghĩa là {@link WordStatus#NOT_STARTED}.
 */
@Repository
public class HskVocabularyQueries {
    private static final String FROM_WORDS_WITH_PROGRESS = """
            FROM hsk_vocabulary v
            LEFT JOIN user_vocabulary_progress p
                   ON p.user_id = :userId AND p.syllabus = v.syllabus
                  AND p.level = v.level AND p.entry_number = v.entry_number
            """;
    private static final String FLASHCARD_SELECT = """
            SELECT v.entry_number, v.hanzi, v.pinyin, v.meaning_vi, v.audio_path, p.status,
                   v.han_viet, v.word_type_vi,
                   e.sentence_zh, e.pinyin AS example_pinyin, e.translation_vi, e.note_vi
            """;
    /** Chỉ lấy câu ví dụ đầu tiên cho mặt sau của thẻ. */
    private static final String FIRST_EXAMPLE = """
            LEFT JOIN hsk_vocabulary_examples e
                   ON e.syllabus = v.syllabus AND e.level = v.level
                  AND e.entry_number = v.entry_number AND e.position = 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HskVocabularyQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TonePracticeQuestion> findTonePractice(String syllabus) {
        String sql = """
                SELECT * FROM (
                    SELECT level, entry_number, hanzi, audio_path, practice_syllable, practice_position,
                           practice_tone, row_number() OVER (PARTITION BY practice_tone ORDER BY random()) AS rank
                    FROM hsk_vocabulary
                    WHERE syllabus = :syllabus AND practice_tone IS NOT NULL AND nullif(audio_path, '') IS NOT NULL
                ) candidates
                ORDER BY rank, random() LIMIT 10
                """;
        var questions = jdbc.query(sql, new MapSqlParameterSource("syllabus", syllabus),
                (rs, i) -> new TonePracticeQuestion(rs.getString("practice_syllable"), rs.getString("audio_path"),
                        rs.getInt("practice_tone"), rs.getInt("practice_position"), rs.getInt("level"),
                        rs.getInt("entry_number"), rs.getString("hanzi")));
        java.util.Collections.shuffle(questions);
        return questions;
    }

    public Optional<DailyWordResponse> findDailyWord(String syllabus, LocalDate date) {
        String sql = """
                SELECT level, entry_number, hanzi, pinyin, meaning_vi, han_viet, word_type_vi, audio_path
                FROM hsk_vocabulary WHERE syllabus = :syllabus
                ORDER BY level, entry_number LIMIT 1
                OFFSET (SELECT mod(:day, nullif(count(*), 0)) FROM hsk_vocabulary WHERE syllabus = :syllabus)
                """;
        var params = new MapSqlParameterSource("syllabus", syllabus).addValue("day", date.toEpochDay());
        return jdbc.query(sql, params, (rs, i) -> new DailyWordResponse(date, rs.getInt("level"),
                rs.getInt("entry_number"), rs.getString("hanzi"), rs.getString("pinyin"), rs.getString("meaning_vi"),
                rs.getString("han_viet"), rs.getString("word_type_vi"), rs.getString("audio_path")))
                .stream().findFirst();
    }

    /** Số từ theo trạng thái của mỗi cấp có dữ liệu. Cấp chưa có từ nào không xuất hiện. */
    public List<LevelCounts> countByLevel(long userId, String syllabus) {
        String sql = """
                SELECT v.level,
                       COUNT(*)                                       AS total_words,
                       COUNT(*) FILTER (WHERE p.status = 'MASTERED') AS mastered,
                       COUNT(*) FILTER (WHERE p.status = 'LEARNING') AS learning
                """ + FROM_WORDS_WITH_PROGRESS + """
                WHERE v.syllabus = :syllabus
                GROUP BY v.level
                """;
        return jdbc.query(sql, params(userId, syllabus), (rs, i) -> new LevelCounts(rs.getInt("level"),
                rs.getInt("total_words"), rs.getInt("mastered"), rs.getInt("learning")));
    }

    public long countWords(long userId, String syllabus, int level, WordStatus status) {
        String sql = "SELECT COUNT(*) " + FROM_WORDS_WITH_PROGRESS + where(status);
        Long count = jdbc.queryForObject(sql, params(userId, syllabus, level, status), Long.class);
        return count == null ? 0 : count;
    }

    /** Một trang từ theo thứ tự giáo trình ({@code entry_number}). */
    public List<HskWordResponse> findWords(long userId, String syllabus, int level, WordStatus status,
            int offset, int limit) {
        String sql = """
                SELECT v.entry_number, v.hanzi, v.pinyin, v.meaning_vi, v.audio_path, p.status
                """ + FROM_WORDS_WITH_PROGRESS + where(status) + """
                ORDER BY v.entry_number
                LIMIT :limit OFFSET :offset
                """;
        var params = params(userId, syllabus, level, status).addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(sql, params, (rs, i) -> new HskWordResponse(rs.getInt("entry_number"),
                rs.getString("hanzi"), rs.getString("pinyin"), rs.getString("meaning_vi"),
                rs.getString("audio_path"), status(rs)));
    }

    public Optional<HskWordDetailResponse> findWord(long userId, String syllabus, int level, int entryNumber) {
        String sql = """
                SELECT v.entry_number, v.level, v.hanzi, v.pinyin, v.meaning_vi, v.audio_path, p.status,
                       v.han_viet, v.han_viet_note_vi, v.word_type_vi, v.usage_note_vi
                """ + FROM_WORDS_WITH_PROGRESS + """
                WHERE v.syllabus = :syllabus AND v.level = :level AND v.entry_number = :entryNumber
                """;
        var params = params(userId, syllabus).addValue("level", level).addValue("entryNumber", entryNumber);
        return jdbc.query(sql, params, (rs, i) -> new HskWordDetailResponse(rs.getInt("entry_number"),
                        rs.getInt("level"), rs.getString("hanzi"), rs.getString("pinyin"),
                        rs.getString("meaning_vi"), rs.getString("audio_path"), status(rs),
                        rs.getString("han_viet"), rs.getString("han_viet_note_vi"), rs.getString("word_type_vi"),
                        rs.getString("usage_note_vi"), findExamples(syllabus, level, entryNumber)))
                .stream().findFirst();
    }

    /** Tổng số từ đến hạn, số từ chưa học và thời điểm đến hạn sớm nhất trong tương lai của một cấp. */
    public FlashcardCounts countFlashcards(long userId, String syllabus, int level, Instant now) {
        String sql = """
                SELECT COUNT(*) FILTER (WHERE p.due_at <= :now)  AS due_count,
                       COUNT(*) FILTER (WHERE p.user_id IS NULL) AS new_count,
                       MIN(p.due_at) FILTER (WHERE p.due_at > :now) AS next_due_at
                """ + FROM_WORDS_WITH_PROGRESS + where(null);
        var params = params(userId, syllabus, level, null).addValue("now", Timestamp.from(now));
        return jdbc.queryForObject(sql, params, (rs, i) -> {
            Timestamp next = rs.getTimestamp("next_due_at");
            return new FlashcardCounts(rs.getLong("due_count"), rs.getLong("new_count"),
                    next == null ? null : next.toInstant());
        });
    }

    /** Từ đến hạn ôn, quá hạn lâu nhất trước. */
    public List<FlashcardResponse> findDueCards(long userId, String syllabus, int level, Instant now, int limit) {
        String sql = FLASHCARD_SELECT + """
                FROM hsk_vocabulary v
                JOIN user_vocabulary_progress p
                  ON p.user_id = :userId AND p.syllabus = v.syllabus
                 AND p.level = v.level AND p.entry_number = v.entry_number
                """ + FIRST_EXAMPLE + """
                WHERE v.syllabus = :syllabus AND v.level = :level AND p.due_at <= :now
                ORDER BY p.due_at, v.entry_number
                LIMIT :limit
                """;
        var params = params(userId, syllabus).addValue("level", level).addValue("now", Timestamp.from(now))
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> card(rs, FlashcardResponse.Reason.DUE));
    }

    /** Từ chưa học theo thứ tự giáo trình. */
    public List<FlashcardResponse> findNewCards(long userId, String syllabus, int level, int limit) {
        String sql = FLASHCARD_SELECT + FROM_WORDS_WITH_PROGRESS + FIRST_EXAMPLE + """
                WHERE v.syllabus = :syllabus AND v.level = :level AND p.user_id IS NULL
                ORDER BY v.entry_number
                LIMIT :limit
                """;
        var params = params(userId, syllabus).addValue("level", level).addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> card(rs, FlashcardResponse.Reason.NEW));
    }

    private static FlashcardResponse card(ResultSet rs, FlashcardResponse.Reason reason) throws SQLException {
        String sentence = rs.getString("sentence_zh");
        var example = sentence == null ? null : new HskWordDetailResponse.Example(sentence,
                rs.getString("example_pinyin"), rs.getString("translation_vi"), rs.getString("note_vi"));
        return new FlashcardResponse(rs.getInt("entry_number"), rs.getString("hanzi"), rs.getString("pinyin"),
                rs.getString("meaning_vi"), rs.getString("audio_path"), status(rs), reason,
                rs.getString("han_viet"), rs.getString("word_type_vi"), example);
    }

    private List<HskWordDetailResponse.Example> findExamples(String syllabus, int level, int entryNumber) {
        String sql = """
                SELECT sentence_zh, pinyin, translation_vi, note_vi
                FROM hsk_vocabulary_examples
                WHERE syllabus = :syllabus AND level = :level AND entry_number = :entryNumber
                ORDER BY position
                """;
        var params = new MapSqlParameterSource("syllabus", syllabus).addValue("level", level)
                .addValue("entryNumber", entryNumber);
        return jdbc.query(sql, params, (rs, i) -> new HskWordDetailResponse.Example(rs.getString("sentence_zh"),
                rs.getString("pinyin"), rs.getString("translation_vi"), rs.getString("note_vi")));
    }

    private static String where(WordStatus status) {
        String base = "WHERE v.syllabus = :syllabus AND v.level = :level\n";
        if (status == null) return base;
        if (status == WordStatus.NOT_STARTED) return base + "  AND p.user_id IS NULL\n";
        return base + "  AND p.status = :status\n";
    }

    private static MapSqlParameterSource params(long userId, String syllabus) {
        return new MapSqlParameterSource("userId", userId).addValue("syllabus", syllabus);
    }

    private static MapSqlParameterSource params(long userId, String syllabus, int level, WordStatus status) {
        var params = params(userId, syllabus).addValue("level", level);
        if (status != null && status != WordStatus.NOT_STARTED) params.addValue("status", status.name());
        return params;
    }

    private static WordStatus status(ResultSet rs) throws SQLException {
        String value = rs.getString("status");
        return value == null ? WordStatus.NOT_STARTED : WordStatus.valueOf(value);
    }

    public record LevelCounts(int level, int totalWords, int mastered, int learning) {
    }

    public record FlashcardCounts(long dueCount, long newCount, Instant nextDueAt) {
    }
}
