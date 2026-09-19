package com.sharvary.billing.payment;

import com.sharvary.billing.common.dto.Dtos.DunningCaseDto;
import com.sharvary.billing.common.dto.Dtos.NotificationDto;
import com.sharvary.billing.payment.dunning.DunningJob;
import com.sharvary.billing.payment.dunning.DunningService;
import com.sharvary.billing.subscription.BillingJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Dunning queue, notification log, and manual job triggers for the admin console. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpsController {

    private final DunningService dunning;
    private final PaymentService payments;
    private final NotificationRepository notifications;
    private final BillingJob billingJob;
    private final DunningJob dunningJob;

    public AdminOpsController(DunningService dunning, PaymentService payments, NotificationRepository notifications,
                              BillingJob billingJob, DunningJob dunningJob) {
        this.dunning = dunning;
        this.payments = payments;
        this.notifications = notifications;
        this.billingJob = billingJob;
        this.dunningJob = dunningJob;
    }

    @GetMapping("/dunning")
    public List<DunningCaseDto> dunningQueue(@RequestParam(defaultValue = "false") boolean includeResolved) {
        var cases = includeResolved ? dunning.all() : dunning.queue();
        int max = dunning.schedule().maxRetries();
        return cases.stream()
                .map(c -> DunningCaseDto.from(c, max, payments.attemptsFor(c.getInvoice().getId())))
                .toList();
    }

    @GetMapping("/notifications")
    public List<NotificationDto> notifications() {
        return notifications.findTop50ByOrderByCreatedAtDesc().stream().map(NotificationDto::from).toList();
    }

    @PostMapping("/jobs/billing/run")
    public Map<String, Integer> runBilling() {
        return Map.of("processed", billingJob.runOnce());
    }

    @PostMapping("/jobs/dunning/run")
    public Map<String, Integer> runDunning() {
        return Map.of("fired", dunningJob.runOnce());
    }
}
