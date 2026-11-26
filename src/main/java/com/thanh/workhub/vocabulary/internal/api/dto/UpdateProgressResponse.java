package com.thanh.workhub.vocabulary.internal.api.dto;

/** Tiến độ của từ vừa đánh dấu và số đếm mới của cấp chứa nó. */
public record UpdateProgressResponse(WordProgressResponse word, HskLevelResponse level) {
}
