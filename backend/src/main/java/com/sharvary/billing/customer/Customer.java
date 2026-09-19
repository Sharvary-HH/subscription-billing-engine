package com.sharvary.billing.customer;

import com.sharvary.billing.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "tax_region", nullable = false)
    private String taxRegion;

    /** Credit carried forward from downgrades and credit notes; applied to the next invoice. */
    @Column(name = "credit_balance_minor", nullable = false)
    private long creditBalanceMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(UUID id, String name, String email, Currency currency, String taxRegion, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.currency = currency.getCurrencyCode();
        this.taxRegion = taxRegion;
        this.creditBalanceMinor = 0;
        this.createdAt = createdAt;
    }

    public Money creditBalance() {
        return Money.of(creditBalanceMinor, currency);
    }

    public void addCredit(Money amount) {
        creditBalanceMinor = creditBalance().plus(amount).minor();
    }

    /** Takes up to {@code upTo} from the balance and returns what was actually taken. */
    public Money drawCredit(Money upTo) {
        Money taken = creditBalance().min(upTo);
        if (taken.isNegative()) {
            taken = Money.zero(currency());
        }
        creditBalanceMinor -= taken.minor();
        return taken;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Currency currency() {
        return Currency.getInstance(currency);
    }

    public String getTaxRegion() {
        return taxRegion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
