package com.sharvary.billing.support;

import com.sharvary.billing.config.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class TestClockConfig {

    public static final Instant START = Instant.parse("2026-01-31T10:00:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(START, ZoneOffset.UTC);
    }
}
