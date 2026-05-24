package com.rikdevos.energy.web.dto;

import com.rikdevos.energy.simulation.Battery;

public record BatterySpecDto(double maxCapacity,
                             double maxPercent,
                             double minPercent,
                             double maxChargePower,
                             double maxDischargePower,
                             double rte,
                             double price) {

    public Battery toBattery() {
        return new Battery(maxCapacity, maxPercent, minPercent,
                maxChargePower, maxDischargePower, rte, price);
    }
}