package com.thanh.workhub.platform.web;
import com.thanh.workhub.user.internal.application.UserNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
 @ExceptionHandler(UserNotFoundException.class)
 public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
  var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  problem.setTitle("Không tìm thấy người dùng");
  problem.setProperty("code", "USER_NOT_FOUND");
  return problem;
 }
}
 