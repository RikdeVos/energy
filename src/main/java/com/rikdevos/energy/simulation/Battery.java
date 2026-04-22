package com.rikdevos.energy.simulation;

/**
 * Mutable state container for the simulated home battery. Kept as a class (not a record)
 * because the simulation walks intervals in order and mutates {@code charge},
 * {@code totalCharged}, and {@code totalDischarged} in place.
 *
 * <p>Visibility note: the mutable fields are <em>package-private</em> so only classes
 * in this package (i.e. {@link BatterySimulator}) can mutate them. External readers
 * (service, reports) use the accessor methods. Config fields are exposed as getters
 * rather than raw fields so the distinction between "what you pass in" and "what the
 * simulator writes" is surfaced at the type's API.
 */
public class Battery {

	// Mutable — written by BatterySimulator, read via the accessors below.
	double charge;                 // kWh — current stored energy
	double totalCharged;           // kWh — cumulative energy put into battery
	double totalDischarged;        // kWh — cumulative energy delivered out of battery (post-loss)

	private final double maxCapacity;       // kWh — nameplate capacity
	private final double maxPercent;        // 0..100 — upper SoC bound
	private final double minPercent;        // 0..100 — lower SoC bound (reserve)
	private final double maxChargePower;    // kW
	private final double maxDischargePower; // kW
	private final double rte;               // round-trip efficiency, 0..1 (applied on discharge)
	private final double price;             // EUR — purchase + install cost, used for ROI

	public Battery(double maxCapacity, double maxPercent, double minPercent,
				   double maxChargePower, double maxDischargePower, double rte, double price) {
		this.maxCapacity = maxCapacity;
		this.maxPercent = maxPercent;
		this.minPercent = minPercent;
		this.maxChargePower = maxChargePower;
		this.maxDischargePower = maxDischargePower;
		this.rte = rte;
		this.price = price;
		this.charge = 0;
	}

	// Package-private reads for the simulator — saves a `.charge()` call in the hot loop.
	double charge() { return charge; }
	double maxChargePower() { return maxChargePower; }
	double maxDischargePower() { return maxDischargePower; }
	double rte() { return rte; }
	double upperLimit() { return maxCapacity * maxPercent / 100.0; }
	double lowerLimit() { return maxCapacity * minPercent / 100.0; }

	// Public reads for external consumers (service/reports).
	public double finalCharge() { return charge; }
	public double totalCharged() { return totalCharged; }
	public double totalDischarged() { return totalDischarged; }
	public double price() { return price; }
	public double usableCapacity() { return upperLimit() - lowerLimit(); }

	// Equivalent full cycles: cumulative energy charged divided by the usable window.
	// Industry-standard wear metric — a LFP pack rated for ~6000 cycles can be compared
	// directly against this number. Uses `totalCharged` (not discharged) because the
	// charge side is what ages the cells; RTE losses don't add wear.
	public double equivalentFullCycles() {
		double usable = usableCapacity();
		return usable > 0 ? totalCharged / usable : 0;
	}
}
