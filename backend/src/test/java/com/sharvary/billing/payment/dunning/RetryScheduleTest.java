package com.sharvary.billing.payment.dunning;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryScheduleTest {

    static final Instant T0 = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void offsetsAreFromTheStartNotThePreviousAttempt() {
        RetrySchedule s = RetrySchedule.parse("1, 3, 5, 7");
        assertThat(s.maxRetries()).isEqualTo(4);
        assertThat(s.nextRetryAt(T0, 0)).contains(T0.plus(Duration.ofDays(1)));
        assertThat(s.nextRetryAt(T0, 1)).contains(T0.plus(Duration.ofDays(3)));
        assertThat(s.nextRetryAt(T0, 2)).contains(T0.plus(Duration.ofDays(5)));
        assertThat(s.nextRetryAt(T0, 3)).contains(T0.plus(Duration.ofDays(7)));
        assertThat(s.nextRetryAt(T0, 4)).isEmpty();
    }

    @Test
    void mustIncrease() {
        assertThatThrownBy(() -> new RetrySchedule(List.of(1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySchedule(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
