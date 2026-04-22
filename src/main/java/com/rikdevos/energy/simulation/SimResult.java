package com.rikdevos.energy.simulation;

/**
 * Result of running the battery simulation: the grid flows that would still occur
 * with a battery inline — export the battery couldn't absorb and import the battery
 * couldn't cover.
 */
public record SimResult(double netImport, double netExport) {
}
