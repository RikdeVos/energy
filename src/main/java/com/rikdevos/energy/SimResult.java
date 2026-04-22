package com.rikdevos.energy;

/**
 * Result of running the battery simulation: the grid flows that would still occur
 * with a battery inline — export the battery couldn't absorb and import the battery
 * couldn't cover. Kept as a plain record so the simulator stays free of Spring and
 * the service can build its {@link CalculationResult} without additional types.
 */
public record SimResult(double netImport, double netExport) {
}
