package com.sharvary.billing.customer;

import com.sharvary.billing.auth.AuthUser;
import com.sharvary.billing.auth.CurrentUser;
import com.sharvary.billing.common.dto.Dtos.CustomerDto;
import com.sharvary.billing.common.dto.Dtos.NotificationDto;
import com.sharvary.billing.common.dto.Dtos.PaymentMethodDto;
import com.sharvary.billing.payment.NotificationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The customer's own record. Every call is scoped to the customer id inside the token. */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerSelfController {

    private final CustomerService customers;
    private final NotificationRepository notifications;

    public CustomerSelfController(CustomerService customers, NotificationRepository notifications) {
        this.customers = customers;
        this.notifications = notifications;
    }

    @GetMapping
    public CustomerDto me() {
        return CustomerDto.from(customers.get(customerId()));
    }

    @GetMapping("/payment-methods")
    public List<PaymentMethodDto> paymentMethods() {
        return customers.paymentMethods(customerId()).stream().map(PaymentMethodDto::from).toList();
    }

    @PostMapping("/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodDto addPaymentMethod(@Valid @RequestBody CustomerAdminController.AddPaymentMethodRequest request) {
        boolean makeDefault = request.makeDefault() == null || request.makeDefault();
        return PaymentMethodDto.from(customers.addPaymentMethod(customerId(), request.token(), request.brand(),
                request.last4(), makeDefault));
    }

    @PutMapping("/payment-methods/{methodId}/default")
    public PaymentMethodDto setDefault(@PathVariable UUID methodId) {
        return PaymentMethodDto.from(customers.setDefault(customerId(), methodId));
    }

    @GetMapping("/notifications")
    public List<NotificationDto> notifications() {
        return notifications.findByCustomerIdOrderByCreatedAtDesc(customerId()).stream().map(NotificationDto::from).toList();
    }

    private static UUID customerId() {
        AuthUser user = CurrentUser.get();
        return user.customerId();
    }
}
