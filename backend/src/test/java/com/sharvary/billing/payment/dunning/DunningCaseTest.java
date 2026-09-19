package com.sharvary.billing.payment.dunning;

import com.sharvary.billing.common.IllegalStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DunningCaseTest {

    static final Instant T0 = Instant.parse("2026-03-01T00:00:00Z");
    static final RetrySchedule SCHEDULE = RetrySchedule.parse("1,3,5,7");

    DunningCase fresh() {
        return new DunningCase(UUID.randomUUID(), null, null, T0, T0.plus(Duration.ofDays(1)));
    }

    @Test
    void walksTheScheduleThenReportsExhaustion() {
        DunningCase c = fresh();
        assertThat(c.getNextRetryAt()).isEqualTo(T0.plus(Duration.ofDays(1)));
        assertThat(c.recordFailedRetry(SCHEDULE)).isTrue();
        assertThat(c.getNextRetryAt()).isEqualTo(T0.plus(Duration.ofDays(3)));
        assertThat(c.recordFailedRetry(SCHEDULE)).isTrue();
        assertThat(c.recordFailedRetry(SCHEDULE)).isTrue();
        assertThat(c.getNextRetryAt()).isEqualTo(T0.plus(Duration.ofDays(7)));
        assertThat(c.recordFailedRetry(SCHEDULE)).isFalse();
        assertThat(c.getNextRetryAt()).isNull();
        assertThat(c.getRetriesDone()).isEqualTo(4);
    }

    @Test
    void restartResetsTheSequenceAndAsksForAnImmediateRetry() {
        DunningCase c = fresh();
        c.recordFailedRetry(SCHEDULE);
        c.recordFailedRetry(SCHEDULE);
        Instant later = T0.plus(Duration.ofDays(4));
        c.restart(later);
        assertThat(c.getRetriesDone()).isZero();
        assertThat(c.getStartedAt()).isEqualTo(later);
        assertThat(c.getNextRetryAt()).isEqualTo(later);
        assertThat(c.recordFailedRetry(SCHEDULE)).isTrue();
        assertThat(c.getNextRetryAt()).isEqualTo(later.plus(Duration.ofDays(3)));
    }

    @Test
    void resolvedCasesCannotBeTouched() {
        DunningCase c = fresh();
        c.resolve(DunningState.RECOVERED, T0);
        assertThat(c.getState()).isEqualTo(DunningState.RECOVERED);
        assertThat(c.getResolvedAt()).isEqualTo(T0);
        assertThat(c.getNextRetryAt()).isNull();
        assertThatThrownBy(() -> c.recordFailedRetry(SCHEDULE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.restart(T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.resolve(DunningState.EXHAUSTED, T0)).isInstanceOf(IllegalStateTransitionException.class);
    }
}
