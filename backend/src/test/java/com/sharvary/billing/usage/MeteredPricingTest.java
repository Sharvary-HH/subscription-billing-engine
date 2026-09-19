package com.sharvary.billing.usage;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.plan.PriceTier;
import com.sharvary.billing.plan.PricingModel;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeteredPricingTest {

    static final Currency USD = Currency.getInstance("USD");
    // 0-1000 @ 10c, 1001-10000 @ 5c, above @ 1c
    static final List<PriceTier.Band> BANDS = List.of(
            new PriceTier.Band(1000L, 10, 0),
            new PriceTier.Band(10000L, 5, 0),
            new PriceTier.Band(null, 1, 0));

    @Test
    void tieredChargesEachBandAtItsOwnRate() {
        assertThat(MeteredPricing.price(PricingModel.TIERED, BANDS, 1500, USD))
                .isEqualTo(Money.of(1000 * 10 + 500 * 5, USD));
        assertThat(MeteredPricing.price(PricingModel.TIERED, BANDS, 12000, USD))
                .isEqualTo(Money.of(1000 * 10 + 9000 * 5 + 2000, USD));
    }

    @Test
    void volumeChargesEverythingAtTheReachedBand() {
        assertThat(MeteredPricing.price(PricingModel.VOLUME, BANDS, 1500, USD)).isEqualTo(Money.of(1500 * 5, USD));
        assertThat(MeteredPricing.price(PricingModel.VOLUME, BANDS, 1000, USD)).isEqualTo(Money.of(1000 * 10, USD));
        assertThat(MeteredPricing.price(PricingModel.VOLUME, BANDS, 12000, USD)).isEqualTo(Money.of(12000, USD));
    }

    @Test
    void theTwoModelsDivergeAtTheBoundary() {
        // One more unit than the first band: volume gets cheaper, tiered gets slightly dearer.
        Money tieredAt1000 = MeteredPricing.price(PricingModel.TIERED, BANDS, 1000, USD);
        Money tieredAt1001 = MeteredPricing.price(PricingModel.TIERED, BANDS, 1001, USD);
        Money volumeAt1000 = MeteredPricing.price(PricingModel.VOLUME, BANDS, 1000, USD);
        Money volumeAt1001 = MeteredPricing.price(PricingModel.VOLUME, BANDS, 1001, USD);
        assertThat(tieredAt1001.compareTo(tieredAt1000)).isGreaterThan(0);
        assertThat(volumeAt1001.compareTo(volumeAt1000)).isLessThan(0);
    }

    @Test
    void zeroUsageIsFree() {
        assertThat(MeteredPricing.price(PricingModel.TIERED, BANDS, 0, USD)).isEqualTo(Money.zero(USD));
        assertThat(MeteredPricing.price(PricingModel.VOLUME, BANDS, 0, USD)).isEqualTo(Money.zero(USD));
    }

    @Test
    void flatFeesApplyPerBandEntered() {
        List<PriceTier.Band> withFees = List.of(new PriceTier.Band(100L, 1, 500), new PriceTier.Band(null, 1, 1000));
        assertThat(MeteredPricing.price(PricingModel.TIERED, withFees, 50, USD)).isEqualTo(Money.of(50 + 500, USD));
        assertThat(MeteredPricing.price(PricingModel.TIERED, withFees, 150, USD)).isEqualTo(Money.of(150 + 500 + 1000, USD));
        assertThat(MeteredPricing.price(PricingModel.VOLUME, withFees, 150, USD)).isEqualTo(Money.of(150 + 1000, USD));
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> MeteredPricing.price(PricingModel.FLAT, BANDS, 1, USD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeteredPricing.price(PricingModel.TIERED, List.of(), 1, USD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeteredPricing.price(PricingModel.TIERED, BANDS, -1, USD)).isInstanceOf(IllegalArgumentException.class);
    }
}
