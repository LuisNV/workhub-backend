package com.thanh.workhub.platform.web;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final int code;
    private final HttpStatus status;

    public ApiException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
