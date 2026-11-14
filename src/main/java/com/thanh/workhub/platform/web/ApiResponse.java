package com.thanh.workhub.platform.web;

/** Envelope chung cho mọi response dưới /api/**. */
public record ApiResponse<T>(int code, int httpStatus, String message, T data, String traceId) {

    public static <T> ApiResponse<T> error(int code, int httpStatus, String message, String traceId) {
        return new ApiResponse<>(code, httpStatus, message, null, traceId);
    }
}
