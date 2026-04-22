# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This project is for learning purposes so output style should be explanatory. Give insights to what you're doing when coding for a sr dev to learn from.

## Build & Test Commands

Gradle wrapper is committed; always use `./gradlew` (not a system `gradle`).

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun`
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests com.rikdevos.energy.EnergyApplicationTests`
- Run a single test method: `./gradlew test --tests 'com.rikdevos.energy.EnergyApplicationTests.contextLoads'`
- Build an OCI image: `./gradlew bootBuildImage`

Test reports land under `build/reports/tests/test/index.html`.

## Toolchain

- Java 25 via Gradle toolchain (`build.gradle` declares `JavaLanguageVersion.of(25)`). Gradle will provision/locate a matching JDK — no need to set `JAVA_HOME` to 25 explicitly, but one must be resolvable.
- Spring Boot **4.0.5** with the Spring Dependency Management plugin 1.1.7. This is the Spring Boot 4.x line (Jakarta EE, Spring Framework 7.x) — not 3.x. Check the 4.0.5 reference docs before assuming APIs; several Boot 3.x idioms have moved or changed.
- JUnit Platform (JUnit 5) via `spring-boot-starter-test`.

## Architecture

A single-purpose Spring Boot app that, on startup, loads a year of 15-minute smart-meter readings and simulates what a home battery + flat tariffs would have done to the electricity bill. All output goes to the log — no web layer, no persistence.

### Classes

- `EnergyApplication` — `@SpringBootApplication` entry point. Just boots the context.
- `CsvLoader` (`ApplicationRunner`) — orchestrator. On startup: reads the CSV, parses rows, diffs cumulative counters into per-interval deltas, runs the battery simulation, invokes `CostCalculator`, and logs all totals + ROI. Holds the nested `Reading`, `Interval`, `SimResult` records and the `Battery` class (mutable state container). The battery parameters and price are constructed inline in `run(...)` — tweak the `new Battery(...)` literal to experiment.
- `Tariffs` (record) — immutable value object, the four per-kWh prices (`importT1/T2`, `exportT1/T2`). `Tariffs.defaults()` returns the user's real contract rates. Split by tariff even when the prices are equal, so future time-of-use work only needs a new calculator.
- `CostCalculator` — single responsibility: multiply kWh flows by tariffs, return EUR. Knows nothing about CSVs or batteries. Depends on `Tariffs` (dependency inversion). `cost(importKwh, exportKwh)` is the one public method.

### Data pipeline (inside `CsvLoader.run`)

1. **Read** — `source.csv` from classpath as `List<String>`.
2. **Parse** — skip header, parse timestamp (`yyyy-MM-dd HH:mm`) and the four kWh columns into `List<Reading>`. L1/L2/L3 columns are dropped.
3. **Diff to intervals** — `toIntervals(readings)` walks pairwise and emits `Interval(time, importKwh, exportKwh)` deltas. **A negative delta is treated as a meter reset** (the CSV has one at `2025-10-13 15:15` where the device was replaced); that one interval is dropped with a `WARN` log. The check is date-free — any future reset behaves the same.
4. **Simulate** — `simulate(battery, intervals)` mutates the `Battery` and returns `SimResult(netImport, netExport)` — the flows that would still hit the grid with the battery inline.
5. **Price** — `CostCalculator` produces `costNoBattery` and `costWithBattery` from the aggregated totals.
6. **Annualize & log** — period span derived from `intervals.getFirst().time()` to `.getLast().time()` via `Duration`, so ROI scales correctly with whatever window the CSV covers. Don't hardcode "1 year" — the code already does the right thing.

### Battery simulation rules (in `simulate`)

- 15-min interval converted to kWh via `INTERVAL_HOURS = 0.25` (kW × 0.25h = kWh). If input resolution changes, change this constant.
- `exportKwh > 0` and `importKwh > 0` branches are **independent `if`s, not `if/else`** — within a single quarter-hour the meter can show both (the home switched modes mid-interval). Early versions used `else if` and silently dropped the second flow.
- Charge amount = `min(exportKwh, headroom, maxChargePerInterval)`. Discharge (delivered to home) = `min(importKwh, available × rte, maxDischargePerInterval)`.
- **RTE (round-trip efficiency) is applied on discharge only.** Delivering `actual` kWh to the home drains `actual / rte` from the cell. `totalDischarged` tracks **delivered** kWh (the useful number — equals the avoided grid import); energy drained from the cell is `totalDischarged / rte`. Default `rte = 0.85`.
- Energy balance invariant (useful for sanity-checking changes): `totalCharged − totalDischarged/rte ≈ finalCharge − initialCharge`.

### Conventions worth preserving

- **Prefer records for value objects** (`Tariffs`, `Reading`, `Interval`, `SimResult`). Classes only when mutation is required (`Battery`).
- **Keep pricing, simulation, and parsing in separate types.** Don't fold `CostCalculator` back into `CsvLoader` even if it's one method — the separation is what makes tariff changes a one-file edit.
- **Don't add a `PricingStrategy` interface until a second implementation exists.** One-implementation interfaces are noise; the seam is already where it needs to be (swap `Tariffs` or instantiate a different calculator).
- `src/main/resources/source.csv` — ~35k rows, one year of 15-min readings. Columns: `time, Import T1 kWh, Import T2 kWh, Export T1 kWh, Export T2 kWh, L1 max W, L2 max W, L3 max W`. Import/export are **cumulative totals** (always increasing until a meter swap).

When adding new code, place it under `com.rikdevos.energy` so component scanning from `EnergyApplication` picks it up without extra configuration.

## Dependencies

Only `spring-boot-starter` (no `-web`, no `-data-jpa`, no `-actuator`) is on the classpath. Adding web endpoints, persistence, or actuator requires adding the corresponding starter to `build.gradle` — don't assume they're already wired.
