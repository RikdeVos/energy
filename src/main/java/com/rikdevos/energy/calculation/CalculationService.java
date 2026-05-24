package com.rikdevos.energy.calculation;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rikdevos.energy.parsing.Interval;
import com.rikdevos.energy.parsing.IntervalBuilder;
import com.rikdevos.energy.parsing.Reading;
import com.rikdevos.energy.parsing.ReadingSource;
import com.rikdevos.energy.pricing.CostCalculator;
import com.rikdevos.energy.pricing.Tariffs;
import com.rikdevos.energy.reporting.EnergyReports;
import com.rikdevos.energy.simulation.Battery;
import com.rikdevos.energy.simulation.BatterySimulator;
import com.rikdevos.energy.simulation.SimResult;

/**
 * Orchestrates a single calculation: read source → build intervals → simulate the
 * battery → price both scenarios → assemble the result. Nothing more. Each
 * responsibility lives on its own collaborator, injected here via the constructor,
 * so this class has exactly one reason to change: the shape of the pipeline itself.
 *
 * <p>The imports above are the package map made concrete — this service is the only
 * file that reaches into every feature package, and that's by design: the orchestrator
 * is the coupling point so the leaf packages stay independent of each other.
 *
 * <p>The input source is a {@link ReadingSource} interface — swapping in a new format
 * (different CSV vendor, JSON API, …) is a new bean implementing that interface; this
 * class does not change.
 */
@Service
public class CalculationService {

    private static final Logger log = LoggerFactory.getLogger(CalculationService.class);

    private final ReadingSource source;
    private final IntervalBuilder intervalBuilder;
    private final BatterySimulator simulator;
    private final EnergyReports reports;

    public CalculationService(ReadingSource source,
                              IntervalBuilder intervalBuilder,
                              BatterySimulator simulator,
                              EnergyReports reports) {
        this.source = source;
        this.intervalBuilder = intervalBuilder;
        this.simulator = simulator;
        this.reports = reports;
    }

    public CalculationResult calculate(InputStream csvStream, Battery battery, Tariffs tariffs) throws IOException {
        List<Reading> readings = source.read(csvStream);
        List<Interval> intervals = intervalBuilder.build(readings);
        log.info("Parsed {} readings, produced {} intervals", readings.size(), intervals.size());

        double totalHomeImport = intervals.stream().mapToDouble(Interval::importKwh).sum();
        double totalHomeExport = intervals.stream().mapToDouble(Interval::exportKwh).sum();

        SimResult sim = simulator.simulate(battery, intervals);

        // CostCalculator is a fresh per-call collaborator rather than an injected bean
        // because it's parameterised on the request's Tariffs — a singleton wouldn't make
        // sense. Still SRP: it only does pricing.
        CostCalculator costs = new CostCalculator(tariffs);
        double costNoBattery = costs.cost(totalHomeImport, totalHomeExport);
        double costWithBattery = costs.cost(sim.netImport(), sim.netExport());
        double periodSavings = costNoBattery - costWithBattery;

        // Annualize from the actual time span in the intervals, so ROI scales with
        // whatever window the dataset happens to cover.
        double periodYears = Duration.between(
                intervals.getFirst().time(), intervals.getLast().time()).toSeconds() / (365.25 * 24 * 3600);
        double annualSavings = periodSavings / periodYears;
        // Sentinel instead of +Infinity — Jackson emits Infinity which isn't valid JSON.
        double roiYears = annualSavings > 0 ? battery.price() / annualSavings : -1.0;

        CalculationResult result = new CalculationResult(
                intervals.size(),
                totalHomeImport,
                totalHomeExport,
                costNoBattery,
                sim.netImport(),
                sim.netExport(),
                costWithBattery,
                battery.finalCharge(),
                battery.totalCharged(),
                battery.totalDischarged(),
                battery.equivalentFullCycles(),
                battery.usableCapacity(),
                periodYears,
                periodSavings,
                annualSavings,
                battery.price(),
                roiYears);

        reports.logSummary(result, battery);
        return result;
    }
}
