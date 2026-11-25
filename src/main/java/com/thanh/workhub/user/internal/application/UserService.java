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
import com.thanh.workhub.user.NewUser;
import com.thanh.workhub.user.UserApi;
import com.thanh.workhub.user.UserCredentials;
import com.thanh.workhub.user.UserProfileResponse;
import com.thanh.workhub.user.internal.api.dto.UpdateProfileRequest;
import com.thanh.workhub.user.internal.domain.User;
import com.thanh.workhub.user.internal.persistence.UserRepository;

@Service
public class UserService implements UserApi {
    private static final String PHONE_UNIQUE_CONSTRAINT = "uk_app_users_phone";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "app_users_email_key";

    private final UserRepository userRepository;
    /** Không gian khóa advisory riêng cho kiểm tra mật khẩu. */
    private static final int PASSWORD_CHECK_LOCK_NAMESPACE = 0x50574348; // "PWCH"

    private final PasswordEncoder passwordEncoder;
    private final PasswordAttemptLimiter attemptLimiter;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            PasswordAttemptLimiter attemptLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
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
            verifyCurrentPassword(id, request.currentPassword());
            if (userRepository.existsByEmail(email)) {
                throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
            }
        }
        // Số điện thoại lưu dạng chỉ có chữ số ("091 234 5678" → "0912345678") để so trùng và đăng nhập.
        String phone = request.phone().replaceAll("\\s+", "");
        if (userRepository.existsByPhoneAndIdNot(phone, id)) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_USED);
        }
        if (emailChanged) user.changeEmail(email);
        user.updateProfile(request.fullName().strip(), phone, request.address().strip(),
                request.gender(), request.dateOfBirth());
        saveHandlingUniqueViolations(user);
        return toProfile(user);
    }

    @Override
    @Transactional
    public UserProfileResponse register(NewUser newUser) {
        if (userRepository.existsByEmail(newUser.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (userRepository.existsByPhone(newUser.phone())) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_USED);
        }
        var user = User.registered(newUser.fullName(), newUser.email(), newUser.phone(),
                newUser.dateOfBirth(), newUser.passwordHash());
        saveHandlingUniqueViolations(user);
        return toProfile(user);
    }

    @Override
    @Transactional
    public void verifyCurrentPassword(Long userId, String rawPassword) {
        userRepository.lockPasswordCheck(PASSWORD_CHECK_LOCK_NAMESPACE, Long.hashCode(userId));
        var user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        attemptLimiter.ensureNotLocked(userId);
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw attemptLimiter.recordFailure(userId);
        }
        attemptLimiter.reset(userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String newPasswordHash) {
        var user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.changePassword(newPasswordHash);
        userRepository.saveAndFlush(user);
    }

    private void saveHandlingUniqueViolations(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Unique constraint xử lý trường hợp hai request cùng lúc chọn một email / số điện thoại.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    if (PHONE_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                        throw new ApiException(ErrorCode.PHONE_ALREADY_USED);
                    }
                    if (EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                        throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
                    }
                }
            }
            throw new ApiException(ErrorCode.DATA_CONFLICT);
        }
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
