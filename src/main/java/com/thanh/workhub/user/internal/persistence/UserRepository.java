package com.thanh.workhub.user.internal.persistence;

import com.thanh.workhub.user.internal.domain.User;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// Chỉ công khai thao tác đọc đang cần; không thêm save/delete khi chưa sử dụng.
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
