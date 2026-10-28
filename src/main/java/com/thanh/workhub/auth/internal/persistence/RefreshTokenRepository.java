
package com.thanh.workhub.auth.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thanh.workhub.user.internal.domain.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM RefreshToken r
        WHERE r.tokenHash = :tokenHash
    """)
    Optional<RefreshToken> findByTokenHashForUpdate(
        @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
        UPDATE RefreshToken r
        SET r.revokedAt = :now
        WHERE r.familyId = :familyId
          AND r.revokedAt IS NULL
    """)
    int revokeFamily(
        @Param("familyId") UUID familyId,
        @Param("now") Instant now
    );
}
