package com.sharvary.billing.reporting;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportingController {

    private final ReportingService reporting;

    public ReportingController(ReportingService reporting) {
        this.reporting = reporting;
    }

    @GetMapping("/dashboard")
    public ReportingService.Dashboard dashboard(@RequestParam(defaultValue = "USD") String currency) {
        return reporting.dashboard(currency);
    }
}
