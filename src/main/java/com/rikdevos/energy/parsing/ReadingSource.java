package com.rikdevos.energy.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Source of cumulative meter {@link Reading}s. One implementation per input format
 * (CSV vendor, JSON API, binary stream). Downstream code ({@link IntervalBuilder},
 * {@code CalculationService}) talks only to this interface, so adding a new format
 * is a new {@code @Component} — nothing else changes.
 *
 * <p>Input is {@link InputStream} rather than {@code List<String>} so non-line-oriented
 * formats (binary, JSON) fit the same seam without buffering the whole payload first.
 */
public interface ReadingSource {
    List<Reading> read(InputStream in) throws IOException;
}
