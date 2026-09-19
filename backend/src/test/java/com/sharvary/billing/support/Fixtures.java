package com.sharvary.billing.support;

import com.sharvary.billing.auth.AuthService;
import com.sharvary.billing.auth.Role;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.customer.CustomerService;
import com.sharvary.billing.plan.BillingInterval;
import com.sharvary.billing.plan.PlanService;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.plan.PricingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Small factory for the things nearly every integration test needs. */
@Component
public class Fixtures {

    public static final Currency USD = Currency.getInstance("USD");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final CustomerService customers;
    private final PlanService plans;
    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public Fixtures(CustomerService customers, PlanService plans, AuthService auth, JdbcTemplate jdbc) {
        this.customers = customers;
        this.plans = plans;
        this.auth = auth;
        this.jdbc = jdbc;
    }

    public void wipe() {
        jdbc.execute("""
                truncate table notifications, dunning_cases, payment_attempts, credit_note_lines, credit_notes,
                    usage_records, invoice_line_items, invoices, subscription_items, subscriptions, price_tiers,
                    plan_versions, plans, payment_methods, users, customers, idempotency_keys
                """);
    }

    public Customer customer(String region) {
        int n = SEQ.incrementAndGet();
        Customer c = customers.create("Customer " + n, "customer" + n + "-" + UUID.randomUUID() + "@test.local", USD, region);
        customers.addPaymentMethod(c.getId(), "tok_ok", "visa", "4242", true);
        return c;
    }

    public Customer customerWithCard(String token) {
        int n = SEQ.incrementAndGet();
        Customer c = customers.create("Customer " + n, "customer" + n + "-" + UUID.randomUUID() + "@test.local", USD, "US");
        customers.addPaymentMethod(c.getId(), token, "visa", "0002", true);
        return c;
    }

    public PlanVersion flatMonthly(long priceMinor) {
        return plans.createPlan("flat-" + priceMinor + "-" + SEQ.incrementAndGet(), "Flat " + priceMinor,
                new PlanService.PriceSpec(USD, BillingInterval.MONTHLY, PricingModel.FLAT, priceMinor, List.of()));
    }

    public PlanVersion perSeatMonthly(long priceMinor) {
        return plans.createPlan("seat-" + priceMinor + "-" + SEQ.incrementAndGet(), "Seat " + priceMinor,
                new PlanService.PriceSpec(USD, BillingInterval.MONTHLY, PricingModel.PER_SEAT, priceMinor, List.of()));
    }

    public PlanVersion tieredMonthly() {
        return plans.createPlan("tiered-" + SEQ.incrementAndGet(), "Tiered",
                new PlanService.PriceSpec(USD, BillingInterval.MONTHLY, PricingModel.TIERED, 0, List.of(
                        new PlanService.TierSpec(100L, 10, 0),
                        new PlanService.TierSpec(null, 5, 0))));
    }

    public String login(String email, String password, Role role, UUID customerId) {
        auth.createUser(email, password, role, customerId);
        return auth.login(email, password).token();
    }
}
