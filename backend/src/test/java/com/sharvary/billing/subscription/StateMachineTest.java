package com.sharvary.billing.subscription;

import com.sharvary.billing.common.IllegalStateTransitionException;
import com.sharvary.billing.invoice.InvoiceStatus;
import com.sharvary.billing.payment.dunning.DunningState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every transition table, exhaustively. The legal set is spelled out so any change to the enums
 * has to be made here too.
 */
class StateMachineTest {

    @Test
    void subscriptionTransitions() {
        assertThat(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.ACTIVE)).isTrue();
        assertThat(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.CANCELED)).isTrue();
        assertThat(SubscriptionStatus.TRIALING.canTransitionTo(SubscriptionStatus.PAST_DUE)).isFalse();
        assertThat(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.PAST_DUE)).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.PAUSED)).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.CANCELED)).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.TRIALING)).isFalse();
        assertThat(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.ACTIVE)).isTrue();
        assertThat(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.CANCELED)).isTrue();
        assertThat(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.PAUSED)).isFalse();
        assertThat(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.ACTIVE)).isTrue();
        assertThat(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.CANCELED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(SubscriptionStatus.class)
    void canceledIsTerminal(SubscriptionStatus target) {
        assertThat(SubscriptionStatus.CANCELED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(SubscriptionStatus.class)
    void noSelfTransitions(SubscriptionStatus s) {
        assertThat(s.canTransitionTo(s)).isFalse();
    }

    @Test
    void billableStates() {
        assertThat(SubscriptionStatus.TRIALING.isBillable()).isTrue();
        assertThat(SubscriptionStatus.ACTIVE.isBillable()).isTrue();
        assertThat(SubscriptionStatus.PAST_DUE.isBillable()).isTrue();
        assertThat(SubscriptionStatus.PAUSED.isBillable()).isFalse();
        assertThat(SubscriptionStatus.CANCELED.isBillable()).isFalse();
    }

    @Test
    void invoiceTransitions() {
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.OPEN)).isTrue();
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.VOID)).isTrue();
        assertThat(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.PAID)).isFalse();
        assertThat(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.PAID)).isTrue();
        assertThat(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.UNCOLLECTIBLE)).isTrue();
        assertThat(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.VOID)).isTrue();
        assertThat(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.DRAFT)).isFalse();
        assertThat(InvoiceStatus.PAID.canTransitionTo(InvoiceStatus.REFUNDED)).isTrue();
        assertThat(InvoiceStatus.PAID.canTransitionTo(InvoiceStatus.OPEN)).isFalse();
        assertThat(InvoiceStatus.PAID.canTransitionTo(InvoiceStatus.VOID)).isFalse();
        for (InvoiceStatus terminal : new InvoiceStatus[]{InvoiceStatus.VOID, InvoiceStatus.UNCOLLECTIBLE, InvoiceStatus.REFUNDED}) {
            for (InvoiceStatus target : InvoiceStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }
        assertThat(InvoiceStatus.DRAFT.isIssued()).isFalse();
        assertThat(InvoiceStatus.OPEN.isIssued()).isTrue();
    }

    @Test
    void dunningTransitions() {
        assertThat(DunningState.RETRYING.canTransitionTo(DunningState.RECOVERED)).isTrue();
        assertThat(DunningState.RETRYING.canTransitionTo(DunningState.EXHAUSTED)).isTrue();
        assertThat(DunningState.RETRYING.canTransitionTo(DunningState.CANCELED)).isTrue();
        for (DunningState terminal : new DunningState[]{DunningState.RECOVERED, DunningState.EXHAUSTED, DunningState.CANCELED}) {
            for (DunningState target : DunningState.values()) {
                assertThat(terminal.canTransitionTo(target)).as("%s -> %s", terminal, target).isFalse();
            }
        }
    }

    @Test
    void entityThrowsOnIllegalMove() {
        Subscription s = new Subscription(java.util.UUID.randomUUID(), null, null, 1, SubscriptionStatus.CANCELED, 1,
                java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 2, 1), null, java.time.Instant.EPOCH);
        assertThatThrownBy(() -> s.transitionTo(SubscriptionStatus.ACTIVE))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("CANCELED").hasMessageContaining("ACTIVE");
        assertThatThrownBy(s::cancelAtPeriodEnd).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> s.rollPeriod(java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
