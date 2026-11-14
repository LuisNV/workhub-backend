package com.thanh.workhub;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fail nếu một module truy cập vào {@code internal} của module khác, hoặc có vòng phụ thuộc giữa module.
 * Không cần Docker hay database.
 */
class ModularityTest {
    private final ApplicationModules modules = ApplicationModules.of(WorkhubApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
