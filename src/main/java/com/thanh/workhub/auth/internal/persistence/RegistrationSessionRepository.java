package com.thanh.workhub.auth.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thanh.workhub.auth.internal.domain.RegistrationSession;

import jakarta.persistence.LockModeType;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, UUID> {

    /** Khóa dòng để hai request cùng phiên (bấm 2 lần, gửi lại mã + xác thực) không ghi đè nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RegistrationSession s where s.id = :id")
    Optional<RegistrationSession> findForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("delete from RegistrationSession s where s.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
