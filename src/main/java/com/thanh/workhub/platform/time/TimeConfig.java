package com.thanh.workhub.platform.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Clock dùng chung (test thay bằng Clock cố định) và bật @Scheduled cho các job dọn dẹp. */
@Configuration
@EnableScheduling
public class TimeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
