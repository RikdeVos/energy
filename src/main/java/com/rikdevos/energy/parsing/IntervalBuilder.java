package com.rikdevos.energy.parsing;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Walks a list of cumulative {@link Reading}s pairwise and emits per-interval
 * {@link Interval} deltas. Owns exactly one business rule: a negative delta means
 * the meter was replaced/reset, so that interval is dropped (with a WARN) rather
 * than letting a large negative value poison the totals.
 *
 * <p>The rule is date-free on purpose — any future meter swap behaves the same way
 * without touching this class.
 */
@Component
public class IntervalBuilder {

    private static final Logger log = LoggerFactory.getLogger(IntervalBuilder.class);

    public List<Interval> build(List<Reading> readings) {
        List<Interval> out = new ArrayList<>(Math.max(0, readings.size() - 1));
        for (int i = 1; i < readings.size(); i++) {
            Reading prev = readings.get(i - 1);
            Reading curr = readings.get(i);
            double importDelta = curr.totalImport() - prev.totalImport();
            double exportDelta = curr.totalExport() - prev.totalExport();
            if (importDelta < 0 || exportDelta < 0) {
                log.warn("Skipping interval at {} — meter reset detected (importΔ={}, exportΔ={})",
                        curr.time(), String.format("%.3f", importDelta), String.format("%.3f", exportDelta));
                continue;
            }
            out.add(new Interval(curr.time(), importDelta, exportDelta));
        }
        return out;
    }
}
