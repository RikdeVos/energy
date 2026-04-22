package com.rikdevos.energy.simulation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.rikdevos.energy.parsing.Interval;

/**
 * Runs a battery against a sequence of {@link Interval}s and returns the grid flows
 * that would remain with the battery inline. Single responsibility: battery dynamics
 * — charge/discharge limits, SoC bounds, round-trip efficiency.
 *
 * <p>The battery is mutated in place because the simulation walks intervals in order
 * and the running SoC is the whole point. Callers pass a fresh {@link Battery} per
 * run to avoid state bleeding across calculations.
 *
 * <p>Within a single quarter-hour the meter can show both import and export — the
 * home switched modes mid-window. So charge and discharge are independent {@code if}s,
 * never {@code if/else}; an early version used {@code else if} and silently dropped
 * the second flow.
 */
@Component
public class BatterySimulator {

	// 15-minute intervals expressed as a fraction of an hour, so kW * INTERVAL_HOURS = kWh.
	// If input resolution changes (e.g. 5-min readings), change this constant.
	private static final double INTERVAL_HOURS = 0.25;

	public SimResult simulate(Battery b, List<Interval> intervals) {
		double maxChargeStep = b.maxChargePower() * INTERVAL_HOURS;
		double maxDischargeStep = b.maxDischargePower() * INTERVAL_HOURS;
		double upper = b.upperLimit();
		double lower = b.lowerLimit();
		double rte = b.rte();

		double netImport = 0;
		double netExport = 0;

		for (Interval it : intervals) {
			if (it.exportKwh() > 0) {
				double headroom = Math.max(0, upper - b.charge);
				double actual = Math.min(Math.min(it.exportKwh(), headroom), maxChargeStep);
				b.charge += actual;
				b.totalCharged += actual;
				netExport += it.exportKwh() - actual;
			}
			if (it.importKwh() > 0) {
				// RTE applies on discharge: delivering `actual` kWh to the home drains
				// `actual / rte` from the cell, so the stored-energy cap limits the
				// *deliverable* side to `available * rte`.
				double available = Math.max(0, b.charge - lower);
				double deliverableFromStore = available * rte;
				double actual = Math.min(Math.min(it.importKwh(), deliverableFromStore), maxDischargeStep);
				b.charge -= actual / rte;
				b.totalDischarged += actual;
				netImport += it.importKwh() - actual;
			}
		}
		return new SimResult(netImport, netExport);
	}
}
