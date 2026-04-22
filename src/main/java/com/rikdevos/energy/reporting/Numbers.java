package com.rikdevos.energy.reporting;

/**
 * Tiny utility for log/report number formatting. Package-private on purpose: consistent
 * three-decimal formatting is a presentation concern and belongs inside this package.
 * Other packages should not reach in for their own formatting needs — if they need to
 * emit numbers, they produce primitives and let {@link EnergyReports} format them.
 */
final class Numbers {

	private Numbers() {}

	static String fmt(double v) {
		return String.format("%.3f", v);
	}
}
