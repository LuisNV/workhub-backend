package com.thanh.workhub.user.internal.application;

import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thanh.workhub.platform.web.ApiException;
import com.thanh.workhub.platform.web.ErrorCode;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;
import com.thanh.workhub.user.UserProfileResponse;
import com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest;
import com.thanh.workhub.user.internal.domain.User;
import com.thanh.workhub.user.internal.persistence.UserRepository;

@Service
public class UserService implements UserApi {
    private static final String PHONE_UNIQUE_CONSTRAINT = "uk_app_users_phone";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(email).map(UserService::toCredentials);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsByPhone(String phone) {
        return userRepository.findByPhone(phone).map(UserService::toCredentials);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findCredentialsById(Long id) {
        return userRepository.findById(id).map(UserService::toCredentials);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long id) {
        var user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        // Chuyển Entity thành DTO trước khi transaction kết thúc.
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long id, UpdateProfileRequest request) {
        var user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        String email = request.email() == null ? user.getEmail()
                : request.email().strip().toLowerCase(Locale.ROOT);
        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new ApiException(ErrorCode.CURRENT_PASSWORD_REQUIRED);
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
            }
            if (userRepository.existsByEmail(email)) {
                throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
            }
        }
        String phone = request.phone().strip();
        if (userRepository.existsByPhoneAndIdNot(phone, id)) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_USED);
        }
        if (emailChanged) user.changeEmail(email);
        user.updateProfile(request.fullName().strip(), phone, request.address().strip(),
                request.gender(), request.dateOfBirth());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Unique constraint xử lý cả hai request cùng chọn một số điện thoại.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && PHONE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                    throw new ApiException(ErrorCode.PHONE_ALREADY_USED);
                }
            }
            throw new ApiException(ErrorCode.DATA_CONFLICT);
        }
        return toProfile(user);
    }

    private static UserCredentials toCredentials(User user) {
        return new UserCredentials(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.isEmailVerified());
    }

    private static UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getAddress(), user.getGender(), user.getDateOfBirth(), user.getAvatarUrl(),
                user.isEmailVerified());
    }
}
