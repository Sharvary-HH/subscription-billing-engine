package com.sharvary.billing.customer;

import com.sharvary.billing.common.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final PaymentMethodRepository paymentMethods;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, PaymentMethodRepository paymentMethods,
                           ApplicationEventPublisher events, Clock clock) {
        this.customers = customers;
        this.paymentMethods = paymentMethods;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Customer create(String name, String email, Currency currency, String taxRegion) {
        customers.findByEmailIgnoreCase(email).ifPresent(c -> {
            throw new IllegalArgumentException("a customer with email " + email + " already exists");
        });
        return customers.save(new Customer(UUID.randomUUID(), name, email, currency, taxRegion, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("customer", id));
    }

    @Transactional(readOnly = true)
    public List<Customer> search(String query) {
        if (query == null || query.isBlank()) {
            return customers.findAll();
        }
        return customers.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethod> paymentMethods(UUID customerId) {
        get(customerId);
        return paymentMethods.findByCustomerIdOrderByCreatedAtAsc(customerId);
    }

    /**
     * Adds a card. A new card becomes the default unless told otherwise, because the common reason
     * to add one is that the old one is failing.
     */
    @Transactional
    public PaymentMethod addPaymentMethod(UUID customerId, String token, String brand, String last4, boolean makeDefault) {
        Customer customer = get(customerId);
        if (makeDefault) {
            paymentMethods.findByCustomerIdAndDefaultMethodTrue(customerId).ifPresent(PaymentMethod::clearDefault);
            paymentMethods.flush();
        }
        PaymentMethod method = paymentMethods.save(
                new PaymentMethod(UUID.randomUUID(), customer, token, brand, last4, makeDefault, clock.instant()));
        if (makeDefault) {
            events.publishEvent(new PaymentMethodUpdatedEvent(customerId));
        }
        return method;
    }

    @Transactional
    public PaymentMethod setDefault(UUID customerId, UUID methodId) {
        PaymentMethod target = paymentMethods.findById(methodId)
                .filter(m -> m.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("payment method", methodId));
        paymentMethods.findByCustomerIdAndDefaultMethodTrue(customerId).ifPresent(PaymentMethod::clearDefault);
        paymentMethods.flush();
        target.markDefault();
        events.publishEvent(new PaymentMethodUpdatedEvent(customerId));
        return target;
    }
}
