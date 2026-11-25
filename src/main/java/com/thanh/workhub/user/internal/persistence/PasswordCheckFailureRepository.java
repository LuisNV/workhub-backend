package com.thanh.workhub.user.internal.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thanh.workhub.user.internal.domain.PasswordCheckFailure;

public interface PasswordCheckFailureRepository extends JpaRepository<PasswordCheckFailure, Long> {

    /** Các lần sai trong khoảng thời gian, cũ nhất đứng đầu (để tính lúc được thử lại). */
    List<PasswordCheckFailure> findByUserIdAndFailedAtAfterOrderByFailedAtAsc(Long userId, Instant since);

    @Modifying
    @Query("delete from PasswordCheckFailure f where f.userId = :userId")
    int deleteByUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PasswordCheckFailure f where f.failedAt < :before")
    int deleteFailedBefore(@Param("before") Instant before);
}
