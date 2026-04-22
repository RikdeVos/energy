package com.rikdevos.energy.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rikdevos.energy.calculation.CalculationResult;
import com.rikdevos.energy.simulation.Battery;

/**
 * Presentation of a completed calculation. Turns a {@link CalculationResult} plus the
 * {@link Battery} it was run against into human-readable log lines. The orchestrator
 * owns "what happens" and this class owns "how we tell the user about it" — the same
 * result can now be rendered to logs, a report file, or a CLI summary without the
 * service caring.
 *
 * <p>Battery is passed in rather than included on the result record because the result
 * already projects the numbers we care about; the battery is only needed for the
 * {@code totalDischarged} → self-consumption log line, and keeping it off the result
 * keeps the JSON response free of mutable-state echoes.
 */
@Component
public class EnergyReports {

	private static final Logger log = LoggerFactory.getLogger(EnergyReports.class);

	public void logSummary(CalculationResult r, Battery battery) {
		log.info("Home totals (no battery): import={} kWh, export={} kWh, cost=€{}",
				Numbers.fmt(r.homeImportKwh()), Numbers.fmt(r.homeExportKwh()), Numbers.fmt(r.costNoBattery()));
		log.info("Home totals (with battery): import={} kWh, export={} kWh, cost=€{}",
				Numbers.fmt(r.netImportKwh()), Numbers.fmt(r.netExportKwh()), Numbers.fmt(r.costWithBattery()));
		log.info("Battery: finalCharge={} kWh, totalCharged={} kWh, totalDischarged={} kWh",
				Numbers.fmt(r.finalChargeKwh()), Numbers.fmt(r.totalChargedKwh()), Numbers.fmt(r.totalDischargedKwh()));
		log.info("Battery cycles: {} equivalent full cycles (usable window {} kWh)",
				Numbers.fmt(r.equivalentFullCycles()), Numbers.fmt(r.usableCapacityKwh()));
		log.info("Self-consumed via battery (= totalDischarged): {} kWh", Numbers.fmt(battery.totalDischarged()));
		log.info("Savings: €{} over {} years → €{}/year. Battery price €{} → ROI {} years",
				Numbers.fmt(r.periodSavings()), Numbers.fmt(r.periodYears()), Numbers.fmt(r.annualSavings()),
				Numbers.fmt(r.batteryPrice()), Numbers.fmt(r.roiYears()));
	}
}
