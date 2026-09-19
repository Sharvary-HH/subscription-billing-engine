package com.sharvary.billing.demo;

import com.sharvary.billing.auth.AuthService;
import com.sharvary.billing.auth.Role;
import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.CustomerService;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.payment.dunning.DunningJob;
import com.sharvary.billing.plan.BillingInterval;
import com.sharvary.billing.plan.PlanService;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.plan.PricingModel;
import com.sharvary.billing.subscription.BillingJob;
import com.sharvary.billing.subscription.BillingService;
import com.sharvary.billing.subscription.Subscription;
import com.sharvary.billing.usage.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Builds the demo dataset by driving the real services with the demo clock: the seeder subscribes
 * customers, then walks the clock forward a month at a time and lets the billing job do its work.
 * Nothing here writes an invoice by hand, so the history a visitor sees is exactly what the
 * engine produces.
 */
@Component
@Profile("demo")
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    public static final String ADMIN_EMAIL = "admin@demo.billing";
    public static final String CUSTOMER_EMAIL = "priya@demo.billing";
    public static final String PASSWORD = "demo1234";

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final MockPaymentProvider provider;
    private final AuthService auth;
    private final CustomerService customers;
    private final PlanService plans;
    private final BillingService billing;
    private final UsageService usage;
    private final BillingJob billingJob;
    private final DunningJob dunningJob;

    private volatile Instant seededAt;

    public DemoSeeder(JdbcTemplate jdbc, MutableClock clock, MockPaymentProvider provider, AuthService auth,
                      CustomerService customers, PlanService plans, BillingService billing, UsageService usage,
                      BillingJob billingJob, DunningJob dunningJob) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.provider = provider;
        this.auth = auth;
        this.customers = customers;
        this.plans = plans;
        this.billing = billing;
        this.usage = usage;
        this.billingJob = billingJob;
        this.dunningJob = dunningJob;
    }

    public Instant seededAt() {
        return seededAt;
    }

    public synchronized void reseed() {
        log.info("reseeding demo data");
        jdbc.execute("""
                truncate table notifications, dunning_cases, payment_attempts, credit_note_lines, credit_notes,
                    usage_records, invoice_line_items, invoices, subscription_items, subscriptions, price_tiers,
                    plan_versions, plans, payment_methods, users, customers, idempotency_keys
                """);
        provider.reset();

        // Start six months back, on the 31st, so the anchor-day clamping shows up in the history.
        Instant realNow = Instant.now();
        LocalDate startDate = LocalDate.ofInstant(realNow, ZoneOffset.UTC).minusMonths(6).withDayOfMonth(1).plusDays(30);
        startDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        clock.set(startDate.atTime(9, 0).toInstant(ZoneOffset.UTC));

        auth.createUser(ADMIN_EMAIL, PASSWORD, Role.ADMIN, null);

        Currency usd = Currency.getInstance("USD");
        PlanVersion starter = plans.createPlan("starter", "Starter", flat(usd, 1900));
        PlanVersion team = plans.createPlan("team", "Team", perSeat(usd, 1200));
        PlanVersion business = plans.createPlan("business", "Business", flat(usd, 9900));
        PlanVersion annual = plans.createPlan("starter-annual", "Starter Annual",
                new PlanService.PriceSpec(usd, BillingInterval.ANNUAL, PricingModel.FLAT, 19000, List.of()));
        PlanVersion apiCalls = plans.createPlan("api-calls", "API calls",
                new PlanService.PriceSpec(usd, BillingInterval.MONTHLY, PricingModel.TIERED, 0, List.of(
                        new PlanService.TierSpec(1000L, 0, 0),
                        new PlanService.TierSpec(10000L, 2, 0),
                        new PlanService.TierSpec(null, 1, 0))));
        PlanVersion storage = plans.createPlan("storage-gb", "Storage (GB)",
                new PlanService.PriceSpec(usd, BillingInterval.MONTHLY, PricingModel.VOLUME, 0, List.of(
                        new PlanService.TierSpec(100L, 25, 0),
                        new PlanService.TierSpec(1000L, 20, 0),
                        new PlanService.TierSpec(null, 15, 0))));

        String[][] people = {
                {"Priya Raman", CUSTOMER_EMAIL, "IN"}, {"Daniel Okafor", "daniel@demo.billing", "GB"},
                {"Mei Chen", "mei@demo.billing", "US"}, {"Lucas Ferreira", "lucas@demo.billing", "US"},
                {"Aisha Khan", "aisha@demo.billing", "GB"}, {"Tomasz Nowak", "tomasz@demo.billing", "DE"},
                {"Hannah Weiss", "hannah@demo.billing", "DE"}, {"Ravi Menon", "ravi@demo.billing", "IN"},
                {"Sofia Rossi", "sofia@demo.billing", "US"}, {"Ben Carter", "ben@demo.billing", "US"},
                {"Yuki Tanaka", "yuki@demo.billing", "US"}, {"Grace Mbeki", "grace@demo.billing", "GB"},
        };
        Customer[] cs = new Customer[people.length];
        for (int i = 0; i < people.length; i++) {
            cs[i] = customers.create(people[i][0], people[i][1], usd, people[i][2]);
            auth.createUser(people[i][1], PASSWORD, Role.CUSTOMER, cs[i].getId());
            // Plain tokens, so the provider's global mode (the "force failures" switch) applies to them.
            String[] brands = {"visa", "mastercard", "amex"};
            String last4 = String.format("%04d", 4000 + i * 37);
            customers.addPaymentMethod(cs[i].getId(), "tok_card_" + last4, brands[i % 3], last4, true);
        }

        Random random = new Random(42);
        Subscription priya = billing.subscribe(cs[0].getId(), team.getId(), 3, 0, List.of(apiCalls.getId()));
        Subscription daniel = billing.subscribe(cs[1].getId(), starter.getId(), 1, 0, List.of());
        Subscription mei = billing.subscribe(cs[2].getId(), business.getId(), 1, 0, List.of(storage.getId()));
        billing.subscribe(cs[3].getId(), annual.getId(), 1, 0, List.of());
        Subscription aisha = billing.subscribe(cs[4].getId(), team.getId(), 5, 0, List.of());
        billing.subscribe(cs[5].getId(), starter.getId(), 1, 0, List.of());
        Subscription hannah = billing.subscribe(cs[6].getId(), business.getId(), 1, 0, List.of());
        billing.subscribe(cs[7].getId(), starter.getId(), 1, 0, List.of(apiCalls.getId()));

        // Month by month: meter usage as the days pass, then let the job bill at the rollover.
        UUID priyaApi = priya.getItems().get(0).getId();
        UUID meiStorage = mei.getItems().get(0).getId();
        for (int month = 0; month < 6; month++) {
            LocalDate monthStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            LocalDate nextStart = monthStart.plusMonths(1);
            long daysInMonth = java.time.temporal.ChronoUnit.DAYS.between(monthStart, nextStart);

            for (int day = 1; day < daysInMonth; day++) {
                clock.set(monthStart.plusDays(day).atTime(9, 0).toInstant(ZoneOffset.UTC));
                if (day % 3 == 0) {
                    usage.record(priyaApi, 300 + random.nextInt(900), clock.instant(), "seed-" + UUID.randomUUID());
                }
                if (day % 5 == 0) {
                    usage.record(meiStorage, 60 + random.nextInt(120), clock.instant(), "seed-" + UUID.randomUUID());
                }
                if (month == 1 && day == 4) {
                    // A couple of later joiners, so the history is not all one cohort.
                    billing.subscribe(cs[8].getId(), team.getId(), 2, 14, List.of());
                    billing.subscribe(cs[9].getId(), starter.getId(), 1, 0, List.of());
                }
                if (month == 2 && day == 11) {
                    // Priya upgrades mid-cycle: the proration event.
                    billing.change(priya.getId(), business.getId(), 1);
                }
                if (month == 3 && day == 2) {
                    // Daniel cancels at period end.
                    billing.cancel(daniel.getId(), false);
                }
                if (month == 3 && day == 7) {
                    billing.subscribe(cs[10].getId(), business.getId(), 1, 0, List.of());
                    billing.subscribe(cs[11].getId(), starter.getId(), 1, 7, List.of());
                }
                if (month == 4 && day == 20) {
                    // Hannah's and Aisha's cards start failing. Their next invoices open dunning cases.
                    customers.addPaymentMethod(cs[6].getId(), "tok_decline", "visa", "0341", true);
                    customers.addPaymentMethod(cs[4].getId(), "tok_decline", "mastercard", "7781", true);
                }
                if (month == 5 && day == 2) {
                    // Aisha updates her card mid-sequence, to another one that declines: an
                    // immediate retry fires and her schedule restarts from today.
                    customers.addPaymentMethod(cs[4].getId(), "tok_decline", "mastercard", "9920", true);
                }
                if (month == 5 && day == 4) {
                    // Stop here: both cases are mid-sequence, with retries still to come.
                    break;
                }
                billingJob.runOnce();
                dunningJob.runOnce();
            }
            if (month == 5) {
                break;
            }
            clock.set(nextStart.atTime(9, 0).toInstant(ZoneOffset.UTC));
            billingJob.runOnce();
            dunningJob.runOnce();
        }

        log.info("demo seeded; clock is at {}, hannah={}, aisha={}", clock.instant(), hannah.getId(), aisha.getId());
        seededAt = Instant.now();
    }

    private static PlanService.PriceSpec flat(Currency c, long minor) {
        return new PlanService.PriceSpec(c, BillingInterval.MONTHLY, PricingModel.FLAT, minor, List.of());
    }

    private static PlanService.PriceSpec perSeat(Currency c, long minor) {
        return new PlanService.PriceSpec(c, BillingInterval.MONTHLY, PricingModel.PER_SEAT, minor, List.of());
    }
}
