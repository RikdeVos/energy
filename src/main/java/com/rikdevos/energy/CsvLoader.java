package com.rikdevos.energy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class CsvLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CsvLoader.class);
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	// 15-minute intervals, expressed as a fraction of an hour, so kW * INTERVAL_HOURS = kWh.
	private static final double INTERVAL_HOURS = 0.25;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<String> lines = readCsv();
		log.info("Loaded {} lines from source.csv", lines.size());

		List<Reading> readings = parse(lines);
		List<Interval> intervals = toIntervals(readings);
		log.info("Parsed {} readings, produced {} intervals", readings.size(), intervals.size());

		double totalHomeImport = intervals.stream().mapToDouble(Interval::importKwh).sum();
		double totalHomeExport = intervals.stream().mapToDouble(Interval::exportKwh).sum();

		Battery battery = new Battery(14.4, 100, 10, 2.4, 2.4, 0.85, 4734);
		SimResult result = simulate(battery, intervals);

		CostCalculator costs = new CostCalculator(Tariffs.defaults());
		double costNoBattery = costs.cost(totalHomeImport, totalHomeExport);
		double costWithBattery = costs.cost(result.netImport(), result.netExport());
		double periodSavings = costNoBattery - costWithBattery;

		// Annualize savings from the actual time span in the CSV, so ROI scales correctly
		// with whatever window the dataset covers — not hardcoded to "a year's worth of rows".
		double periodYears = Duration.between(
				intervals.getFirst().time(), intervals.getLast().time()).toSeconds() / (365.25 * 24 * 3600);
		double annualSavings = periodSavings / periodYears;
		double roiYears = annualSavings > 0 ? battery.price / annualSavings : Double.POSITIVE_INFINITY;

		log.info("Home totals (no battery): import={} kWh, export={} kWh, cost=€{}",
				fmt(totalHomeImport), fmt(totalHomeExport), fmt(costNoBattery));
		log.info("Home totals (with battery): import={} kWh, export={} kWh, cost=€{}",
				fmt(result.netImport()), fmt(result.netExport()), fmt(costWithBattery));
		log.info("Battery: finalCharge={} kWh, totalCharged={} kWh, totalDischarged={} kWh",
				fmt(battery.charge), fmt(battery.totalCharged), fmt(battery.totalDischarged));
		log.info("Self-consumed via battery (= totalDischarged): {} kWh", fmt(battery.totalDischarged));
		log.info("Savings: €{} over {} years → €{}/year. Battery price €{} → ROI {} years",
				fmt(periodSavings), fmt(periodYears), fmt(annualSavings),
				fmt(battery.price), fmt(roiYears));
	}

	private List<String> readCsv() throws Exception {
		ClassPathResource resource = new ClassPathResource("source.csv");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines().toList();
		}
	}

	// Skip the header row; split CSV, parse timestamp + the four kWh columns. L1/L2/L3 are dropped.
	static List<Reading> parse(List<String> lines) {
		List<Reading> out = new ArrayList<>(lines.size());
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.isEmpty()) continue;
			String[] p = line.split(",");
			LocalDateTime time = LocalDateTime.parse(p[0].trim(), TIME_FMT);
			double importT1 = Double.parseDouble(p[1].trim());
			double importT2 = Double.parseDouble(p[2].trim());
			double exportT1 = Double.parseDouble(p[3].trim());
			double exportT2 = Double.parseDouble(p[4].trim());
			out.add(new Reading(time, importT1, importT2, exportT1, exportT2));
		}
		return out;
	}

	// Cumulative counters become per-interval deltas. A negative delta means the meter was
	// replaced/reset (e.g. 2025-10-13 15:15), so we drop that single interval rather than
	// letting a huge negative number poison the totals.
	static List<Interval> toIntervals(List<Reading> readings) {
		List<Interval> out = new ArrayList<>(Math.max(0, readings.size() - 1));
		for (int i = 1; i < readings.size(); i++) {
			Reading prev = readings.get(i - 1);
			Reading curr = readings.get(i);
			double importDelta = curr.totalImport() - prev.totalImport();
			double exportDelta = curr.totalExport() - prev.totalExport();
			if (importDelta < 0 || exportDelta < 0) {
				log.warn("Skipping interval at {} — meter reset detected (importΔ={}, exportΔ={})",
						curr.time(), fmt(importDelta), fmt(exportDelta));
				continue;
			}
			out.add(new Interval(curr.time(), importDelta, exportDelta));
		}
		return out;
	}

	// Walk intervals in order, mutating battery state. Handle charge and discharge
	// independently: within a single 15-min window the meter can show both import and
	// export (home switched between drawing and feeding back). All caps (capacity,
	// min/max %, power) fall out of a three-way min(). Returns the net grid totals the
	// home would see with the battery inline: export the battery didn't absorb, and
	// import the battery couldn't cover.
	static SimResult simulate(Battery b, List<Interval> intervals) {
		double maxChargeStep = b.maxChargePower * INTERVAL_HOURS;
		double maxDischargeStep = b.maxDischargePower * INTERVAL_HOURS;
		double upper = b.upperLimit();
		double lower = b.lowerLimit();

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
				// `actual / rte` from the cell. So the stored-energy cap limits the
				// *deliverable* side to `available * rte`.
				double available = Math.max(0, b.charge - lower);
				double deliverableFromStore = available * b.rte;
				double actual = Math.min(Math.min(it.importKwh(), deliverableFromStore), maxDischargeStep);
				b.charge -= actual / b.rte;
				b.totalDischarged += actual;
				netImport += it.importKwh() - actual;
			}
		}
		return new SimResult(netImport, netExport);
	}

	record SimResult(double netImport, double netExport) {}

	private static String fmt(double v) {
		return String.format("%.3f", v);
	}

	record Reading(LocalDateTime time, double importT1, double importT2,
				   double exportT1, double exportT2) {
		double totalImport() { return importT1 + importT2; }
		double totalExport() { return exportT1 + exportT2; }
	}

	record Interval(LocalDateTime time, double importKwh, double exportKwh) {}
}
