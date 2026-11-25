package com.thanh.workhub.auth.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thanh.workhub.auth.internal.domain.LoginSession;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

    /** Phiên còn hiệu lực của user, thiết bị dùng gần nhất đứng đầu. */
    @Query("""
        select s from LoginSession s
        where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
        order by s.lastActiveAt desc
    """)
    List<LoginSession> findActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    Optional<LoginSession> findByIdAndUserId(UUID id, Long userId);

    /** Phiên chưa kết thúc (kể cả đã quá hạn) của cùng một máy. */
    List<LoginSession> findByUserIdAndDeviceIdAndRevokedAtIsNull(Long userId, String deviceId);

    /**
     * Khóa theo user trong transaction hiện tại (Postgres advisory lock), để hai lần đăng nhập cùng lúc
     * không cùng thấy "còn chỗ" và vượt giới hạn thiết bị. Tự nhả khi transaction kết thúc.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:userId)) as locked", nativeQuery = true)
    Integer lockUser(@Param("userId") long userId);
}
