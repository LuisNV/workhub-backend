package com.thanh.workhub.vocabulary.internal.domain;

/** Trạng thái học một từ. {@link #NOT_STARTED} không lưu trong DB: chưa có dòng tiến độ nào. */
public enum WordStatus {
    NOT_STARTED,
    LEARNING,
    MASTERED
}
