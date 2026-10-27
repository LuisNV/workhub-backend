package com.thanh.workhub.user.internal.api;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
 private final UserService userService;
 public UserController(UserService userService) { this.userService = userService; }
 @GetMapping("/{id}")
 public UserProfileResponse getProfile(@PathVariable("id") Long id) {
  if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID phải là số nguyên dương");
  return userService.getProfile(id);
 }
}
