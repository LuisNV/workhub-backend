package com.thanh.workhub.user.internal.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest;
import com.thanh.workhub.user.internal.api.dto.UserProfileResponse;
import com.thanh.workhub.user.internal.persistence.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserProfileResponse updateProfile(Long id, UpdateProfileRequest request) {
        var user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        String email = request.email() == null ? user.getEmail()
                : request.email().strip().toLowerCase(java.util.Locale.ROOT);
        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new com.thanh.workhub.platform.web.ApiException(1001,
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Vui lòng nhập mật khẩu hiện tại để đổi email");
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new com.thanh.workhub.platform.web.ApiException(1002,
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
            }
            if (userRepository.existsByEmail(email)) {
                throw new com.thanh.workhub.platform.web.ApiException(1003,
                        org.springframework.http.HttpStatus.CONFLICT, "Email đã được sử dụng");
            }
        }
        String phone = request.phone().strip();
        if (userRepository.existsByPhoneAndIdNot(phone, id)) {
            throw phoneAlreadyUsed();
        }
        if (emailChanged) user.changeEmail(email);
        user.updateProfile(request.fullName().strip(), phone, request.address().strip(),
                request.gender(), request.dateOfBirth());
        try {
            userRepository.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            // Unique constraint xử lý cả hai request cùng chọn một số điện thoại.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uk_app_users_phone".equals(violation.getConstraintName())) {
                    throw phoneAlreadyUsed();
                }
            }
            throw new com.thanh.workhub.platform.web.ApiException(409,
                    org.springframework.http.HttpStatus.CONFLICT, "Thông tin cập nhật bị trùng hoặc không hợp lệ");
        }
        return getProfile(id);
    }

    private com.thanh.workhub.platform.web.ApiException phoneAlreadyUsed() {
        return new com.thanh.workhub.platform.web.ApiException(1004,
                org.springframework.http.HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long id) {
        var user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        // Chuyển Entity thành DTO trước khi transaction kết thúc.
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAddress(), user.getGender(), user.getDateOfBirth(), user.getAvatarUrl(),user.isEmailVerified());
    }
}
