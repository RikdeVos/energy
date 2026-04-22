package com.rikdevos.energy.pricing;

/**
 * Prices energy flows using a {@link Tariffs} snapshot. Single responsibility: turn kWh
 * quantities into EUR. Knows nothing about CSV rows, batteries, or simulation — it just
 * does multiplication. This keeps the pricing concern testable in isolation and lets the
 * same calculator be reused for baseline-cost, simulated-cost, and "what-if" scenarios.
 *
 * <p>Current callers aggregate T1+T2 before calling, because both tariffs are identical
 * in the defaults. If a future tariff has different T1/T2 prices, add an overload that
 * takes the four quantities separately; the tariffs record already holds them split.
 */
public class CostCalculator {

	private final Tariffs tariffs;

	public CostCalculator(Tariffs tariffs) {
		this.tariffs = tariffs;
	}

	/**
	 * Net cost in EUR for a flow: positive = money paid to the grid, negative = money
	 * received. Uses the T1 price on both sides — valid while T1 == T2, which the defaults
	 * guarantee. A {@code cost(double, double, double, double)} overload would be the
	 * extension point for tariff splits.
	 */
	public double cost(double importKwh, double exportKwh) {
		return importKwh * tariffs.importT1() - exportKwh * tariffs.exportT1();
	}

	public Tariffs tariffs() {
		return tariffs;
	}
}
