package com.thanh.workhub.auth.internal.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thanh.workhub.auth.internal.domain.RegistrationOtpSend;

public interface RegistrationOtpSendRepository extends JpaRepository<RegistrationOtpSend, Long> {

    long countByEmailAndSentAtAfter(String email, Instant since);

    @Modifying
    @Query("delete from RegistrationOtpSend s where s.sentAt < :before")
    int deleteSentBefore(@Param("before") Instant before);
}
