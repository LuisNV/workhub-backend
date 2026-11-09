package com.thanh.workhub.user.internal.api;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.UserService;
import com.thanh.workhub.user.internal.application.AvatarService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
 private final UserService userService;
 private final AvatarService avatarService;
 public UserController(UserService userService, AvatarService avatarService) {
  this.userService = userService;
  this.avatarService = avatarService;
 }
 @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
 public UserProfileResponse updateAvatar(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
  Long userId;
  try {
   userId = Long.valueOf(jwt.getSubject());
   if (userId <= 0) throw new NumberFormatException();
  } catch (NumberFormatException exception) {
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token không có user ID hợp lệ");
  }
  return avatarService.updateAvatar(userId, file);
 }
 @GetMapping("/{id}")
 public UserProfileResponse getProfile(@PathVariable("id") Long id) {
  if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID phải là số nguyên dương");
  return userService.getProfile(id);
 }
}
