package com.sharvary.billing.payment.dunning;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Retry offsets measured from when the sequence started, not from the previous attempt. Day 1, 3,
 * 5, 7 by default: four retries over a week, then the invoice is written off.
 */
public record RetrySchedule(List<Integer> days) {

    public RetrySchedule {
        if (days.isEmpty()) {
            throw new IllegalArgumentException("retry schedule cannot be empty");
        }
        for (int i = 1; i < days.size(); i++) {
            if (days.get(i) <= days.get(i - 1)) {
                throw new IllegalArgumentException("retry days must strictly increase");
            }
        }
        days = List.copyOf(days);
    }

    public static RetrySchedule parse(String csv) {
        return new RetrySchedule(java.util.Arrays.stream(csv.split(",")).map(String::trim).map(Integer::parseInt).toList());
    }

    public int maxRetries() {
        return days.size();
    }

    /** When retry number {@code retriesDone + 1} is due, or empty when the schedule is spent. */
    public Optional<Instant> nextRetryAt(Instant startedAt, int retriesDone) {
        if (retriesDone >= days.size()) {
            return Optional.empty();
        }
        return Optional.of(startedAt.plus(Duration.ofDays(days.get(retriesDone))));
    }
}
