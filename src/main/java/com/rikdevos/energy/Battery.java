package com.rikdevos.energy;

// Mutable state container for the simulated home battery. Kept as a class (not a record)
// because the simulation walks intervals in order and mutates `charge`, `totalCharged`,
// and `totalDischarged` in place. Configuration fields are final; only the running
// counters change. Pulled out of CsvLoader so the simulator/orchestrator doesn't own
// the battery's shape — single responsibility.
class Battery {
	double charge;                  // kWh — current stored energy
	final double maxCapacity;       // kWh — nameplate capacity
	final double maxPercent;        // 0..100 — upper SoC bound
	final double minPercent;        // 0..100 — lower SoC bound (reserve)
	final double maxChargePower;    // kW
	final double maxDischargePower; // kW
	final double rte;               // round-trip efficiency, 0..1 (applied on discharge)
	final double price;             // EUR — purchase + install cost, used for ROI
	double totalCharged;            // kWh — cumulative energy put into battery
	double totalDischarged;         // kWh — cumulative energy delivered out of battery (post-loss)

	Battery(double maxCapacity, double maxPercent, double minPercent,
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

	double upperLimit() { return maxCapacity * maxPercent / 100.0; }
	double lowerLimit() { return maxCapacity * minPercent / 100.0; }
}
