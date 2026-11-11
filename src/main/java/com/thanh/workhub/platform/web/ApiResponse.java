package com.thanh.workhub.platform.web;

public record ApiResponse<T>(int code, int httpStatus, String message, T data, String traceId) {
}
