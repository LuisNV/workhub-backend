package com.thanh.workhub.auth.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thanh.workhub.auth.internal.domain.PasswordResetRequest;

import jakarta.persistence.LockModeType;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordResetRequest r where r.id = :id")
    Optional<PasswordResetRequest> findForUpdate(@Param("id") UUID id);

    /** Yêu cầu còn mở (chờ nhập mã / chờ đặt mật khẩu) của user, để hủy sau khi đặt lại thành công. */
    @Query("""
        select r from PasswordResetRequest r
        where r.userId = :userId
          and r.status in (com.thanh.workhub.auth.internal.domain.PasswordResetRequest.Status.PENDING,
                           com.thanh.workhub.auth.internal.domain.PasswordResetRequest.Status.VERIFIED)
    """)
    List<PasswordResetRequest> findOpenByUserId(@Param("userId") Long userId);

    long countByIdentifierAndCreatedAtAfter(String identifier, Instant since);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);

    /**
     * Khóa theo email/số điện thoại trong transaction hiện tại (Postgres advisory lock), để các request song
     * song không cùng lọt qua giới hạn số yêu cầu mỗi giờ. Tự nhả khi transaction kết thúc.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:namespace, :key)) as locked", nativeQuery = true)
    Integer lockIdentifier(@Param("namespace") int namespace, @Param("key") int key);

    @Modifying
    @Query("delete from PasswordResetRequest r where r.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
