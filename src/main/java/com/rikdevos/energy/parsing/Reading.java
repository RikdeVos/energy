package com.rikdevos.energy.parsing;

import java.time.LocalDateTime;

/**
 * One row from the smart-meter CSV: a timestamp and four cumulative kWh counters
 * (import on T1/T2, export on T1/T2). Cumulative — these numbers only go up until
 * the meter is physically replaced.
 */
public record Reading(LocalDateTime time, double importT1, double importT2,
                      double exportT1, double exportT2) {

    public double totalImport() {
        return importT1 + importT2;
    }

    public double totalExport() {
        return exportT1 + exportT2;
    }
}
