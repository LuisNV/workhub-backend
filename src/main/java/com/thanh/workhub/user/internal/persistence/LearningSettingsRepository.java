package com.thanh.workhub.user.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thanh.workhub.user.internal.domain.LearningSettings;

public interface LearningSettingsRepository extends JpaRepository<LearningSettings, Long> {
}
