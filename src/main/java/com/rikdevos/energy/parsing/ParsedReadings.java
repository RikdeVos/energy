package com.rikdevos.energy.parsing;

import java.util.List;

/**
 * Result of parsing a meter export: the valid {@link Reading}s plus a count of rows
 * that were skipped because they failed validation. The skip count is surfaced (not
 * just logged) so the orchestrator can include it in error responses when too few
 * valid rows remain to run a simulation.
 */
public record ParsedReadings(List<Reading> readings, int skippedRows) {
}
