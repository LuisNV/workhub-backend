package com.thanh.workhub.vocabulary.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.vocabulary.internal.api.dto.FlashcardResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.FlashcardSessionResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskLevelResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordDetailResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordPageResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.HskWordResponse;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;
import com.thanh.workhub.vocabulary.internal.persistence.HskVocabularyQueries;

/** Màn Từ vựng HSK: tab cấp + tiến độ, danh sách từ theo bộ lọc, chi tiết từ. Chỉ đọc. */
@Service
public class HskVocabularyService {
    static final int MIN_LEVEL = 1;
    static final int MAX_LEVEL = 9;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_FLASHCARDS = 50;

    private final HskVocabularyQueries queries;
    private final Clock clock;
    private final String syllabus;

    public HskVocabularyService(HskVocabularyQueries queries, Clock clock,
            @Value("${app.hsk.syllabus}") String syllabus) {
        this.queries = queries;
        this.clock = clock;
        this.syllabus = syllabus;
    }

    /** Luôn trả đủ 9 cấp; cấp chưa có từ nào thì {@code available = false}. */
    @Transactional(readOnly = true)
    public List<HskLevelResponse> levels(long userId) {
        Map<Integer, HskVocabularyQueries.LevelCounts> counts = queries.countByLevel(userId, syllabus).stream()
                .collect(Collectors.toMap(HskVocabularyQueries.LevelCounts::level, Function.identity()));
        var result = new ArrayList<HskLevelResponse>(MAX_LEVEL);
        for (int level = MIN_LEVEL; level <= MAX_LEVEL; level++) {
            var c = counts.getOrDefault(level, new HskVocabularyQueries.LevelCounts(level, 0, 0, 0));
            result.add(new HskLevelResponse(level, title(level), c.totalWords() > 0, c.totalWords(),
                    c.mastered(), c.learning(), c.totalWords() - c.mastered() - c.learning()));
        }
        return result;
    }

    /** {@code status = null} là "Tất cả". Cấp chưa có dữ liệu trả trang rỗng. */
    @Transactional(readOnly = true)
    public HskWordPageResponse words(long userId, int level, WordStatus status, int page, int size) {
        requireLevel(level);
        if (page < 0) throw new ApiException(ErrorCode.BAD_REQUEST, "page phải lớn hơn hoặc bằng 0");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        long total = queries.countWords(userId, syllabus, level, status);
        long offset = (long) page * size;
        var items = offset >= total ? List.<HskWordResponse>of()
                : queries.findWords(userId, syllabus, level, status, (int) offset, size);
        int totalPages = (int) ((total + size - 1) / size);
        return new HskWordPageResponse(items, page, size, total, totalPages);
    }

    /**
     * Thẻ cho một lượt học: từ đến hạn ôn trước (quá hạn lâu nhất trước), còn chỗ thì thêm từ mới theo thứ tự
     * giáo trình. Chỉ đọc: tiến độ được ghi khi gửi kết quả về {@code POST /hsk/reviews}.
     */
    @Transactional(readOnly = true)
    public FlashcardSessionResponse flashcards(long userId, int level, int size) {
        requireLevel(level);
        if (size < 1 || size > MAX_FLASHCARDS) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "size phải từ 1 đến " + MAX_FLASHCARDS);
        }
        Instant now = clock.instant();
        var counts = queries.countFlashcards(userId, syllabus, level, now);
        var items = new ArrayList<FlashcardResponse>(size);
        if (counts.dueCount() > 0) items.addAll(queries.findDueCards(userId, syllabus, level, now, size));
        int remaining = size - items.size();
        if (remaining > 0 && counts.newCount() > 0) {
            items.addAll(queries.findNewCards(userId, syllabus, level, remaining));
        }
        return new FlashcardSessionResponse(level, items, counts.dueCount(), counts.newCount(), counts.nextDueAt());
    }

    @Transactional(readOnly = true)
    public HskWordDetailResponse word(long userId, int level, int entryNumber) {
        requireLevel(level);
        return queries.findWord(userId, syllabus, level, entryNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.HSK_WORD_NOT_FOUND));
    }

    static String title(int level) {
        if (level <= 3) return "Sơ cấp";
        if (level <= 6) return "Trung cấp";
        return "Cao cấp";
    }

    private static void requireLevel(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cấp HSK phải từ " + MIN_LEVEL + " đến " + MAX_LEVEL);
        }
    }
}
