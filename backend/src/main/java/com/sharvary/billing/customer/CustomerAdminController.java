package com.sharvary.billing.customer;

import com.sharvary.billing.auth.AuthService;
import com.sharvary.billing.auth.Role;
import com.sharvary.billing.common.dto.Dtos.CustomerDto;
import com.sharvary.billing.common.dto.Dtos.PaymentMethodDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/customers")
public class CustomerAdminController {

    private final CustomerService customers;
    private final AuthService auth;

    public CustomerAdminController(CustomerService customers, AuthService auth) {
        this.customers = customers;
        this.auth = auth;
    }

    public record CreateCustomerRequest(@NotBlank String name, @NotBlank @Email String email,
                                        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                                        @NotBlank String taxRegion,
                                        @Size(min = 8, max = 72) String password) {
    }

    public record AddPaymentMethodRequest(@NotBlank String token, @NotBlank String brand,
                                          @NotBlank @Pattern(regexp = "\\d{4}") String last4, Boolean makeDefault) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDto create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = customers.create(request.name(), request.email(), Currency.getInstance(request.currency()),
                request.taxRegion());
        if (request.password() != null) {
            auth.createUser(request.email(), request.password(), Role.CUSTOMER, customer.getId());
        }
        return CustomerDto.from(customer);
    }

    @GetMapping
    public List<CustomerDto> search(@RequestParam(required = false) String q) {
        return customers.search(q).stream().map(CustomerDto::from).toList();
    }

    @GetMapping("/{id}")
    public CustomerDto get(@PathVariable UUID id) {
        return CustomerDto.from(customers.get(id));
    }

    @GetMapping("/{id}/payment-methods")
    public List<PaymentMethodDto> paymentMethods(@PathVariable UUID id) {
        return customers.paymentMethods(id).stream().map(PaymentMethodDto::from).toList();
    }

    @PostMapping("/{id}/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodDto addPaymentMethod(@PathVariable UUID id, @Valid @RequestBody AddPaymentMethodRequest request) {
        boolean makeDefault = request.makeDefault() == null || request.makeDefault();
        return PaymentMethodDto.from(customers.addPaymentMethod(id, request.token(), request.brand(), request.last4(), makeDefault));
    }

    @PutMapping("/{id}/payment-methods/{methodId}/default")
    public PaymentMethodDto setDefault(@PathVariable UUID id, @PathVariable UUID methodId) {
        return PaymentMethodDto.from(customers.setDefault(id, methodId));
    }
}
