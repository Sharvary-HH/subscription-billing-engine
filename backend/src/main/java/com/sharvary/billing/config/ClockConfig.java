package com.sharvary.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

/**
 * The only place the system clock is read. Every service takes a {@link Clock}; tests pass a
 * fixed one, and the demo profile swaps in a {@link MutableClock} so time can be advanced by hand.
 */
@Configuration
public class ClockConfig {

    @Bean
    @Profile("!demo")
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("demo")
    public MutableClock demoClock() {
        return new MutableClock(Clock.systemUTC());
    }
}
