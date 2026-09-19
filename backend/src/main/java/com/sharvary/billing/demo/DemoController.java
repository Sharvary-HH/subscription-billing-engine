package com.sharvary.billing.demo;

import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.payment.dunning.DunningJob;
import com.sharvary.billing.subscription.BillingJob;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * The time-travel control panel. Only exists under the demo profile, and only for admins.
 * Advancing the clock runs the same jobs the scheduler would; nothing is simulated.
 */
@RestController
@RequestMapping("/api/demo")
@Profile("demo")
public class DemoController {

    private final MutableClock clock;
    private final MockPaymentProvider provider;
    private final BillingJob billingJob;
    private final DunningJob dunningJob;
    private final DemoSeeder seeder;

    public DemoController(MutableClock clock, MockPaymentProvider provider, BillingJob billingJob, DunningJob dunningJob,
                          DemoSeeder seeder) {
        this.clock = clock;
        this.provider = provider;
        this.billingJob = billingJob;
        this.dunningJob = dunningJob;
        this.seeder = seeder;
    }

    public record Info(boolean enabled, Instant now, MockPaymentProvider.Mode paymentMode, Instant seededAt,
                       String adminEmail, String customerEmail, String password) {
    }

    public record AdvanceRequest(@Min(1) @Max(366) int days) {
    }

    public record AdvanceResponse(Instant now, int subscriptionsBilled, int retriesFired) {
    }

    public record PaymentModeRequest(@NotNull MockPaymentProvider.Mode mode) {
    }

    @GetMapping("/info")
    public Info info() {
        return new Info(true, clock.instant(), provider.getMode(), seeder.seededAt(), DemoSeeder.ADMIN_EMAIL,
                DemoSeeder.CUSTOMER_EMAIL, DemoSeeder.PASSWORD);
    }

    /**
     * Moves the clock forward a day at a time so retries land on the days they are scheduled for,
     * rather than all firing at once at the end of the jump.
     */
    @PostMapping("/advance")
    public AdvanceResponse advance(@Valid @RequestBody AdvanceRequest request) {
        int billed = 0;
        int fired = 0;
        for (int i = 0; i < request.days(); i++) {
            clock.advance(Duration.ofDays(1));
            billed += billingJob.runOnce();
            fired += dunningJob.runOnce();
        }
        return new AdvanceResponse(clock.instant(), billed, fired);
    }

    @PostMapping("/payment-mode")
    public Info paymentMode(@Valid @RequestBody PaymentModeRequest request) {
        provider.setMode(request.mode());
        return info();
    }

    @PostMapping("/reset")
    public Info reset() {
        seeder.reseed();
        return info();
    }
}
