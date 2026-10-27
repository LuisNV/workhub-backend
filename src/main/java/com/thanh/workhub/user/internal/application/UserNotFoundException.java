package com.thanh.workhub.user.internal.application;
public class UserNotFoundException extends RuntimeException {
 public UserNotFoundException(Long id) { super("Không tìm thấy người dùng có ID " + id); }
}
