package com.sharvary.billing.usage;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.plan.PriceTier;
import com.sharvary.billing.plan.PricingModel;

import java.util.Currency;
import java.util.List;

/**
 * Prices a usage quantity against a set of bands. Pure.
 *
 * <p>The two models are easy to confuse and behave very differently at the boundaries:
 *
 * <ul>
 *   <li><b>Tiered</b> (graduated): each band charges its own units at its own rate, like income
 *       tax. 1,500 units on bands [0-1000 @ 0.10] [1000+ @ 0.05] costs 1000 x 0.10 + 500 x 0.05.</li>
 *   <li><b>Volume</b>: the band the total lands in sets the rate for <i>every</i> unit. The same
 *       1,500 units cost 1500 x 0.05. Buying one more unit can make the whole bill cheaper.</li>
 * </ul>
 *
 * Each band may also carry a flat fee, charged once when the band is entered.
 */
public final class MeteredPricing {

    private MeteredPricing() {
    }

    public static Money price(PricingModel model, List<PriceTier.Band> bands, long quantity, Currency currency) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        if (bands.isEmpty()) {
            throw new IllegalArgumentException("metered pricing needs at least one band");
        }
        return switch (model) {
            case TIERED -> tiered(bands, quantity, currency);
            case VOLUME -> volume(bands, quantity, currency);
            case FLAT, PER_SEAT -> throw new IllegalArgumentException(model + " is not a metered model");
        };
    }

    static Money tiered(List<PriceTier.Band> bands, long quantity, Currency currency) {
        long total = 0;
        long lowerBound = 0;
        for (PriceTier.Band band : bands) {
            if (quantity <= lowerBound) {
                break;
            }
            long upper = band.upTo() == null ? Long.MAX_VALUE : band.upTo();
            long unitsInBand = Math.min(quantity, upper) - lowerBound;
            total = Math.addExact(total, Math.multiplyExact(unitsInBand, band.unitPriceMinor()));
            total = Math.addExact(total, band.flatFeeMinor());
            lowerBound = upper;
        }
        return Money.of(total, currency);
    }

    static Money volume(List<PriceTier.Band> bands, long quantity, Currency currency) {
        if (quantity == 0) {
            return Money.zero(currency);
        }
        PriceTier.Band applicable = bands.get(bands.size() - 1);
        for (PriceTier.Band band : bands) {
            if (band.upTo() == null || quantity <= band.upTo()) {
                applicable = band;
                break;
            }
        }
        long total = Math.addExact(Math.multiplyExact(quantity, applicable.unitPriceMinor()), applicable.flatFeeMinor());
        return Money.of(total, currency);
    }
}
