package com.thanh.workhub.user.internal.api;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.application.AvatarService;
import com.thanh.workhub.user.internal.application.UserService;

import jakarta.validation.Valid;
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
  return avatarService.updateAvatar(currentUserId(jwt), file);
 }
 @PutMapping("/me")
 public UserProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
   @Valid @RequestBody UpdateProfileRequest request) {
  return userService.updateProfile(currentUserId(jwt), request);
 }
 private Long currentUserId(Jwt jwt) {
  try {
   if (jwt == null) throw new NumberFormatException();
   long userId = Long.parseLong(jwt.getSubject());
   if (userId <= 0) throw new NumberFormatException();
   return userId;
  } catch (NumberFormatException exception) {
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token không có user ID hợp lệ");
  }
 }
 @GetMapping("/{id}")
 public UserProfileResponse getProfile(@PathVariable("id") Long id) {
  if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID phải là số nguyên dương");
  return userService.getProfile(id);
 }
}
