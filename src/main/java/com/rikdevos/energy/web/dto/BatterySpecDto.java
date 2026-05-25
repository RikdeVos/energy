package com.rikdevos.energy.web.dto;

import com.rikdevos.energy.simulation.Battery;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

public record BatterySpecDto(
        @DecimalMin(value = "0.0", inclusive = false) double maxCapacity,
        @DecimalMin("0.0") @DecimalMax("100.0") double maxPercent,
        @DecimalMin("0.0") @DecimalMax("100.0") double minPercent,
        @DecimalMin(value = "0.0", inclusive = false) double maxChargePower,
        @DecimalMin(value = "0.0", inclusive = false) double maxDischargePower,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") double rte,
        @PositiveOrZero double price) {

    // Cross-field invariant: a zero-or-inverted SoC window means there's nothing usable
    // and the simulator's `usableCapacity()` would be <= 0. Caught here so the request
    // is rejected at the HTTP boundary, not in the middle of simulation.
    @AssertTrue(message = "minPercent must be strictly less than maxPercent")
    public boolean isSocWindowValid() {
        return minPercent < maxPercent;
    }

    public Battery toBattery() {
        return new Battery(maxCapacity, maxPercent, minPercent,
                maxChargePower, maxDischargePower, rte, price);
    }
}