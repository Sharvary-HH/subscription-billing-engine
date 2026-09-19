package com.sharvary.billing.payment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An in-memory provider you can point at a wall.
 *
 * <p>Behaviour is chosen by a global mode plus per-token overrides ({@code tok_decline} always
 * declines, {@code tok_timeout} always times out). Results are remembered by idempotency key, so a
 * retry after a timeout gets the original answer and the charge counter does not move: exactly the
 * guarantee a real provider gives, which is what makes retrying after a timeout safe.
 *
 * <p>When a timeout happens the charge is, by default, treated as having gone through on the
 * provider side. That is the nasty case: the money moved and we were not told.
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    public enum Mode {
        SUCCEED,
        DECLINE,
        TIMEOUT
    }

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.SUCCEED);
    private final Map<String, ChargeResult> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger chargesMade = new AtomicInteger();
    private final AtomicInteger refundsMade = new AtomicInteger();
    private volatile boolean timeoutActuallyCharges = true;

    @Override
    public ChargeResult charge(ChargeRequest request) {
        ChargeResult remembered = byKey.get(request.idempotencyKey());
        if (remembered != null) {
            // Replay. Whatever was decided the first time stands, including a timeout that
            // actually charged: the provider knows, we did not.
            return remembered.outcome() == Outcome.TIMED_OUT && timeoutActuallyCharges
                    ? ChargeResult.succeeded(reference(request))
                    : remembered;
        }

        Mode effective = mode.get();
        String token = request.paymentMethodToken();
        if ("tok_decline".equals(token)) {
            effective = Mode.DECLINE;
        } else if ("tok_timeout".equals(token)) {
            effective = Mode.TIMEOUT;
        } else if ("tok_ok".equals(token)) {
            effective = Mode.SUCCEED;
        }

        ChargeResult result = switch (effective) {
            case SUCCEED -> {
                chargesMade.incrementAndGet();
                yield ChargeResult.succeeded(reference(request));
            }
            case DECLINE -> ChargeResult.declined("card_declined");
            case TIMEOUT -> {
                if (timeoutActuallyCharges) {
                    chargesMade.incrementAndGet();
                }
                yield ChargeResult.timedOut();
            }
        };
        byKey.put(request.idempotencyKey(), result);
        return result;
    }

    @Override
    public void refund(RefundRequest request) {
        refundsMade.incrementAndGet();
    }

    public void setMode(Mode newMode) {
        mode.set(newMode);
    }

    public Mode getMode() {
        return mode.get();
    }

    public void setTimeoutActuallyCharges(boolean value) {
        this.timeoutActuallyCharges = value;
    }

    public int chargesMade() {
        return chargesMade.get();
    }

    public int refundsMade() {
        return refundsMade.get();
    }

    public void reset() {
        mode.set(Mode.SUCCEED);
        byKey.clear();
        chargesMade.set(0);
        refundsMade.set(0);
        timeoutActuallyCharges = true;
    }

    private static String reference(ChargeRequest request) {
        return "ch_" + UUID.nameUUIDFromBytes(request.idempotencyKey().getBytes()).toString().substring(0, 12);
    }
}
