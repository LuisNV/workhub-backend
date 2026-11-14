package com.thanh.workhub.user.internal.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.thanh.workhub.platform.security.CurrentUserId;
import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserProfileResponse;
import com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest;
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

    @GetMapping("/{id}")
    public UserProfileResponse getProfile(@PathVariable("id") Long id) {
        if (id <= 0) throw new ApiException(ErrorCode.INVALID_ID);
        return userService.getProfile(id);
    }

    @PutMapping("/me")
    public UserProfileResponse updateProfile(@CurrentUserId Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }

    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    public UserProfileResponse updateAvatar(@CurrentUserId Long userId,
            @RequestParam("file") MultipartFile file) {
        return avatarService.updateAvatar(userId, file);
    }
}
