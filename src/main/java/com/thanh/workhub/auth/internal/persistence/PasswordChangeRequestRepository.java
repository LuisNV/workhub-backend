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

import com.thanh.workhub.auth.internal.domain.PasswordChangeRequest;

import jakarta.persistence.LockModeType;

public interface PasswordChangeRequestRepository extends JpaRepository<PasswordChangeRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordChangeRequest r where r.id = :id and r.userId = :userId")
    Optional<PasswordChangeRequest> findForUpdate(@Param("id") UUID id, @Param("userId") Long userId);

    @Query("""
        select r from PasswordChangeRequest r
        where r.userId = :userId and r.status = com.thanh.workhub.auth.internal.domain.PasswordChangeRequest.Status.PENDING
    """)
    List<PasswordChangeRequest> findPendingByUserId(@Param("userId") Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    @Modifying
    @Query("delete from PasswordChangeRequest r where r.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
