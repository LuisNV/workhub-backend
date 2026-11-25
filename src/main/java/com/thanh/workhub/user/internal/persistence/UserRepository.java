package com.thanh.workhub.user.internal.persistence;

import com.thanh.workhub.user.internal.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForAvatarUpdate(@Param("id") Long id);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, Long id);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /**
     * Khóa kiểm tra mật khẩu của một user trong transaction hiện tại (Postgres advisory lock, không khóa dòng),
     * để nhiều request song song không cùng lọt qua giới hạn số lần sai. Tự nhả khi transaction kết thúc.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    Integer lockPasswordCheck(@Param("namespace") int namespace, @Param("key") int key);
}
