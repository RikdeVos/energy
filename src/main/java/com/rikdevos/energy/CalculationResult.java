package com.rikdevos.energy;

/**
 * Every number the summary log prints, flattened into a single JSON-friendly record
 * so the HTTP endpoint can return it verbatim. Flat on purpose — Jackson serializes
 * it without custom config, and callers pick the fields they care about.
 *
 * <p>Note on {@code roiYears}: when savings are ≤ 0 we use {@code -1.0} as a sentinel
 * instead of {@link Double#POSITIVE_INFINITY}. Jackson's default config emits
 * {@code Infinity}, which isn't valid JSON — downstream parsers reject it. {@code -1}
 * is unambiguous here because ROI is always positive when the battery is profitable.
 */
public record CalculationResult(
		int intervalsParsed,
		double homeImportKwh,
		double homeExportKwh,
		double costNoBattery,
		double netImportKwh,
		double netExportKwh,
		double costWithBattery,
		double finalChargeKwh,
		double totalChargedKwh,
		double totalDischargedKwh,
		double equivalentFullCycles,
		double usableCapacityKwh,
		double periodYears,
		double periodSavings,
		double annualSavings,
		double batteryPrice,
		double roiYears
) {}
