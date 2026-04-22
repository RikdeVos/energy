package com.rikdevos.energy;

/**
 * Flat per-kWh prices (EUR/kWh) for the four meter quadrants. Value object: no behavior,
 * just data. Kept separate from {@link CostCalculator} so alternative tariff sources
 * (config file, API, time-of-use schedules) can produce a {@code Tariffs} snapshot
 * without the calculator knowing the difference.
 */
public record Tariffs(double importT1, double importT2, double exportT1, double exportT2) {

	/**
	 * Project defaults: energy price + taxes on import (T1 and T2 equal), flat 0.10 on export.
	 * Source: user's contract.
	 */
	public static Tariffs defaults() {
		double importPrice = 0.23236;
//		double exportPrice = 0.13010;
		double exportPrice = 0.02;
		return new Tariffs(importPrice, importPrice, exportPrice, exportPrice);
	}
}
