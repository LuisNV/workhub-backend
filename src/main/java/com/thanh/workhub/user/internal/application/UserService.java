package com.thanh.workhub.user.internal.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.persistence.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long id) {
        var user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        // Chuyển Entity thành DTO trước khi transaction kết thúc.
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAddress());
    }
}
