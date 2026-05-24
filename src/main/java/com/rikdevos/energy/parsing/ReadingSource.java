package com.rikdevos.energy.parsing;

import java.io.IOException;
import java.io.InputStream;

/**
 * Source of cumulative meter {@link Reading}s. One implementation per input format
 * (CSV vendor, JSON API, binary stream). Downstream code ({@link IntervalBuilder},
 * {@code CalculationService}) talks only to this interface, so adding a new format
 * is a new {@code @Component} — nothing else changes.
 *
 * <p>Input is {@link InputStream} rather than {@code List<String>} so non-line-oriented
 * formats (binary, JSON) fit the same seam without buffering the whole payload first.
 *
 * <p>Returns {@link ParsedReadings} so implementations can report rows that were
 * skipped because of per-row validation failures. Implementations are expected to be
 * tolerant of bad rows (skip + warn) and reserve thrown exceptions for unrecoverable
 * problems with the input as a whole (e.g. a missing required column).
 */
public interface ReadingSource {
    ParsedReadings read(InputStream in) throws IOException;
}
