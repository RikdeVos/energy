package com.rikdevos.energy;

/**
 * Tiny utility for log/report number formatting. Extracted so the simulator, the
 * interval builder, and the reports class all use the same three-decimal style
 * without each defining its own helper. Not a responsibility worth a whole class —
 * but not worth scattering {@code String.format("%.3f", v)} calls either.
 */
final class Numbers {

	private Numbers() {}

	static String fmt(double v) {
		return String.format("%.3f", v);
	}
}
