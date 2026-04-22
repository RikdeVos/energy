package com.rikdevos.energy;

import java.time.LocalDateTime;

/**
 * Per-interval delta produced from two consecutive {@link Reading}s: how many kWh
 * were imported and exported during that 15-minute window. Within a single interval
 * both values can be positive — the home can have switched mode mid-window.
 */
public record Interval(LocalDateTime time, double importKwh, double exportKwh) {
}
