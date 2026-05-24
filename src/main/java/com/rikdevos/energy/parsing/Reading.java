package com.rikdevos.energy.parsing;

import java.time.LocalDateTime;

/**
 * One reading from a smart meter: a timestamp and two cumulative kWh counters
 * (total import, total export). Cumulative — these numbers only go up until the
 * meter is physically replaced.
 *
 * <p>Format-agnostic: any vendor-specific quadrant breakdown (e.g. HomeWizard's
 * T1/T2 tariffs) is summed at parse time by the {@link ReadingSource} implementation,
 * so downstream code never needs to know which source it came from.
 */
public record Reading(LocalDateTime time, double totalImport, double totalExport) {
}
