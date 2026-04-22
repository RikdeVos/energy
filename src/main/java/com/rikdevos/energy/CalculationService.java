package com.rikdevos.energy;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rikdevos.energy.CsvLoader.Interval;
import com.rikdevos.energy.CsvLoader.Reading;
import com.rikdevos.energy.CsvLoader.SimResult;

/**
 * Orchestrator lifted out of the old {@code CsvLoader.run(...)} method. Same sequence,
 * same log lines — but the three inputs that used to be hardcoded (CSV bytes, battery
 * specs, tariffs) are now parameters, and the method returns a {@link CalculationResult}
 * instead of being {@code void}.
 *
 * <p>Keeping the parsing/diffing/simulation helpers where they were (as statics on
 * {@link CsvLoader}) is deliberate — the user wants the bigger "extract each concern into
 * its own type" refactor done separately.
 */
@Service
public class CalculationService {

	private static final Logger log = LoggerFactory.getLogger(CalculationService.class);

	public CalculationResult calculate(List<String> csvLines, Battery battery, Tariffs tariffs) {
		log.info("Loaded {} lines from source.csv", csvLines.size());

		List<Reading> readings = CsvLoader.parse(csvLines);
		List<Interval> intervals = CsvLoader.toIntervals(readings);
		log.info("Parsed {} readings, produced {} intervals", readings.size(), intervals.size());

		double totalHomeImport = intervals.stream().mapToDouble(Interval::importKwh).sum();
		double totalHomeExport = intervals.stream().mapToDouble(Interval::exportKwh).sum();

		SimResult result = CsvLoader.simulate(battery, intervals);

		CostCalculator costs = new CostCalculator(tariffs);
		double costNoBattery = costs.cost(totalHomeImport, totalHomeExport);
		double costWithBattery = costs.cost(result.netImport(), result.netExport());
		double periodSavings = costNoBattery - costWithBattery;

		// Annualize savings from the actual time span in the intervals, so ROI scales
		// correctly with whatever window the dataset covers.
		double periodYears = Duration.between(
				intervals.getFirst().time(), intervals.getLast().time()).toSeconds() / (365.25 * 24 * 3600);
		double annualSavings = periodSavings / periodYears;
		// Sentinel instead of +Infinity — see CalculationResult.roiYears javadoc.
		double roiYears = annualSavings > 0 ? battery.price / annualSavings : -1.0;

		log.info("Home totals (no battery): import={} kWh, export={} kWh, cost=€{}",
				CsvLoader.fmt(totalHomeImport), CsvLoader.fmt(totalHomeExport), CsvLoader.fmt(costNoBattery));
		log.info("Home totals (with battery): import={} kWh, export={} kWh, cost=€{}",
				CsvLoader.fmt(result.netImport()), CsvLoader.fmt(result.netExport()), CsvLoader.fmt(costWithBattery));
		log.info("Battery: finalCharge={} kWh, totalCharged={} kWh, totalDischarged={} kWh",
				CsvLoader.fmt(battery.charge), CsvLoader.fmt(battery.totalCharged), CsvLoader.fmt(battery.totalDischarged));
		log.info("Battery cycles: {} equivalent full cycles (usable window {} kWh)",
				CsvLoader.fmt(battery.equivalentFullCycles()), CsvLoader.fmt(battery.usableCapacity()));
		log.info("Self-consumed via battery (= totalDischarged): {} kWh", CsvLoader.fmt(battery.totalDischarged));
		log.info("Savings: €{} over {} years → €{}/year. Battery price €{} → ROI {} years",
				CsvLoader.fmt(periodSavings), CsvLoader.fmt(periodYears), CsvLoader.fmt(annualSavings),
				CsvLoader.fmt(battery.price), CsvLoader.fmt(roiYears));

		return new CalculationResult(
				intervals.size(),
				totalHomeImport,
				totalHomeExport,
				costNoBattery,
				result.netImport(),
				result.netExport(),
				costWithBattery,
				battery.charge,
				battery.totalCharged,
				battery.totalDischarged,
				battery.equivalentFullCycles(),
				battery.usableCapacity(),
				periodYears,
				periodSavings,
				annualSavings,
				battery.price,
				roiYears);
	}
}
