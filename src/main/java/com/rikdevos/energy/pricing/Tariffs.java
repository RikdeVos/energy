package com.rikdevos.energy.pricing;

/**
 * Flat per-kWh prices (EUR/kWh) for the four meter quadrants. Value object: no behavior,
 * just data. Kept separate from {@link CostCalculator} so alternative tariff sources
 * (config file, API, time-of-use schedules) can produce a {@code Tariffs} snapshot
 * without the calculator knowing the difference.
 */
public record Tariffs(double importT1, double importT2, double exportT1, double exportT2) {
}
