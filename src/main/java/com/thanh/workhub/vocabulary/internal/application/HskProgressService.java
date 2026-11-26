package com.thanh.workhub.vocabulary.internal.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.vocabulary.internal.api.dto.HskLevelResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.ReviewRequest;
import com.thanh.workhub.vocabulary.internal.api.dto.ReviewResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.UpdateProgressResponse;
import com.thanh.workhub.vocabulary.internal.api.dto.WordProgressResponse;
import com.thanh.workhub.vocabulary.internal.domain.WordProgress;
import com.thanh.workhub.vocabulary.internal.domain.WordStatus;
import com.thanh.workhub.vocabulary.internal.persistence.HskProgressRepository;
import com.thanh.workhub.vocabulary.internal.persistence.HskProgressRepository.WordKey;

/** Ghi tiến độ học từ: kết quả lượt học thẻ, hoặc người dùng tự đánh dấu. Lịch ôn xem {@link WordProgress}. */
@Service
public class HskProgressService {
    private static final int LOCK_NAMESPACE = 0x48534B50; // "HSKP"

    private final HskProgressRepository progress;
    private final HskVocabularyService vocabulary;
    private final Clock clock;
    private final String syllabus;

    public HskProgressService(HskProgressRepository progress, HskVocabularyService vocabulary, Clock clock,
            @Value("${app.hsk.syllabus}") String syllabus) {
        this.progress = progress;
        this.vocabulary = vocabulary;
        this.clock = clock;
        this.syllabus = syllabus;
    }

    /**
     * Áp dụng các thẻ theo thứ tự gửi lên, trong một transaction: một thẻ không tồn tại thì không ghi gì.
     * Khóa theo user để hai lượt gửi cùng lúc không ghi đè lẫn nhau.
     */
    @Transactional
    public ReviewResponse review(long userId, ReviewRequest request) {
        var words = new LinkedHashSet<WordKey>();
        request.items().forEach(item -> words.add(new WordKey(item.level(), item.entryNumber())));
        requireWords(words);

        progress.lockUser(LOCK_NAMESPACE, userId);
        Instant now = clock.instant();
        Map<WordKey, WordProgress> state = new LinkedHashMap<>(progress.find(userId, syllabus, words));
        for (var item : request.items()) {
            var word = new WordKey(item.level(), item.entryNumber());
            state.put(word, WordProgress.review(state.get(word), item.result(), now));
        }

        var items = new ArrayList<WordProgressResponse>(words.size());
        for (var word : words) {
            var updated = state.get(word);
            progress.save(userId, syllabus, word, updated);
            items.add(toResponse(word, updated));
        }
        Set<Integer> levels = new LinkedHashSet<>();
        words.forEach(word -> levels.add(word.level()));
        return new ReviewResponse(items, levelCounts(userId, levels));
    }

    /** Đặt trạng thái một từ. Đã ở đúng trạng thái đó thì giữ nguyên lịch ôn hiện tại. */
    @Transactional
    public UpdateProgressResponse update(long userId, int level, int entryNumber, WordStatus status) {
        if (level < HskVocabularyService.MIN_LEVEL || level > HskVocabularyService.MAX_LEVEL) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cấp HSK phải từ 1 đến 9");
        }
        var word = new WordKey(level, entryNumber);
        requireWords(Set.of(word));

        progress.lockUser(LOCK_NAMESPACE, userId);
        Instant now = clock.instant();
        WordProgress current = progress.find(userId, syllabus, Set.of(word)).get(word);
        WordProgress updated = switch (status) {
            case NOT_STARTED -> null;
            case LEARNING -> current != null && current.status() == WordStatus.LEARNING
                    ? current : WordProgress.learning(now);
            case MASTERED -> current != null && current.status() == WordStatus.MASTERED
                    ? current : WordProgress.mastered(now);
        };
        if (updated == null) {
            progress.delete(userId, syllabus, word);
        } else if (updated != current) {
            progress.save(userId, syllabus, word, updated);
        }
        return new UpdateProgressResponse(toResponse(word, updated), levelCounts(userId, Set.of(level)).get(0));
    }

    private void requireWords(Set<WordKey> words) {
        Set<WordKey> existing = progress.existingWords(syllabus, words);
        for (var word : words) {
            if (!existing.contains(word)) {
                throw new ApiException(ErrorCode.HSK_WORD_NOT_FOUND,
                        "Không tìm thấy từ số " + word.entryNumber() + " của HSK " + word.level());
            }
        }
    }

    private List<HskLevelResponse> levelCounts(long userId, Set<Integer> levels) {
        return vocabulary.levels(userId).stream().filter(level -> levels.contains(level.level())).toList();
    }

    private static WordProgressResponse toResponse(WordKey word, WordProgress progress) {
        if (progress == null) {
            return new WordProgressResponse(word.level(), word.entryNumber(), WordStatus.NOT_STARTED, 0, null, null);
        }
        return new WordProgressResponse(word.level(), word.entryNumber(), progress.status(),
                progress.correctStreak(), progress.dueAt(), progress.lastReviewedAt());
    }
}
