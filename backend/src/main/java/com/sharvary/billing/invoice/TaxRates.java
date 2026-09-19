package com.sharvary.billing.invoice;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tax rate by customer tax region. A static table is enough here; a real system would call a tax
 * service, and the invoice calculator does not care where the rate comes from.
 */
public final class TaxRates {

    private static final Map<String, BigDecimal> RATES = Map.of(
            "IN", new BigDecimal("0.18"),
            "GB", new BigDecimal("0.20"),
            "DE", new BigDecimal("0.19"),
            "FR", new BigDecimal("0.20"),
            "US", BigDecimal.ZERO,
            "CA", new BigDecimal("0.05"),
            "AU", new BigDecimal("0.10")
    );

    private TaxRates() {
    }

    public static BigDecimal forRegion(String region) {
        return RATES.getOrDefault(region, BigDecimal.ZERO);
    }
}
