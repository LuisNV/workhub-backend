package com.thanh.workhub.user.internal.application;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;

public class UserNotFoundException extends ApiException {
    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng có ID " + id);
    }
}
