# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This project is for learning purposes so output style should be explanatory. Give insights to what you're doing when coding for a sr dev to learn from.

## Build & Test Commands

Gradle wrapper is committed; always use `./gradlew` (not a system `gradle`).

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun` (starts embedded Tomcat on :8080)
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests com.rikdevos.energy.EnergyApplicationTests`
- Run a single test method: `./gradlew test --tests 'com.rikdevos.energy.EnergyApplicationTests.contextLoads'`
- Run the e2e integration test: `./gradlew test --tests com.rikdevos.energy.CalculateControllerIT`
- Build an OCI image: `./gradlew bootBuildImage`

Test reports land under `build/reports/tests/test/index.html`.

### Hitting the endpoint manually

`requests.http` at the project root is an IntelliJ HTTP Client file with a ready-made multipart POST to `/calculate`. Run `./gradlew bootRun` in one terminal, then click the green ▶ in IntelliJ on the request. The CSV is uploaded from `./src/main/resources/source.csv` via the `<` file-reference syntax — paths are resolved relative to the `.http` file's directory.

## Toolchain

- Java 25 via Gradle toolchain (`build.gradle` declares `JavaLanguageVersion.of(25)`). Gradle will provision/locate a matching JDK — no need to set `JAVA_HOME` to 25 explicitly, but one must be resolvable.
- Spring Boot **4.0.5** with the Spring Dependency Management plugin 1.1.7. This is the Spring Boot 4.x line (Jakarta EE, Spring Framework 7.x) — not 3.x. Check the 4.0.5 reference docs before assuming APIs; several Boot 3.x idioms have moved or changed.
- JUnit Platform (JUnit 5) via `spring-boot-starter-test`.

## Architecture

Single-purpose Spring Boot app behind one HTTP endpoint. `POST /calculate` takes a CSV of 15-minute smart-meter readings plus a battery spec and a tariff snapshot, and returns what a battery + flat tariffs would have done to the electricity bill. No persistence.

### Package layout (feature-based)

Every package has one reason to change. The orchestrator (`calculation`) is the only place that reaches across packages; leaf packages don't depend on each other.

```
com.rikdevos.energy
├── EnergyApplication            @SpringBootApplication — component scan root
├── web/                         CalculateController (+ BatterySpec request DTO)
├── parsing/                     CsvReadingParser, IntervalBuilder, Reading, Interval
├── simulation/                  Battery, BatterySimulator, SimResult
├── pricing/                     Tariffs, CostCalculator
├── reporting/                   EnergyReports (log summary), Numbers (pkg-private fmt util)
└── calculation/                 CalculationService (orchestrator), CalculationResult
```

### Classes

- `CsvReadingParser` — raw CSV lines → `List<Reading>`. Knows only the column order and the timestamp pattern.
- `IntervalBuilder` — cumulative readings → per-interval deltas. Owns the meter-reset rule (negative delta → drop interval with a WARN).
- `BatterySimulator` — runs a `Battery` against a list of `Interval`s. Mutates the battery in place; returns the net grid flows as `SimResult`. Encapsulates charge/discharge limits, SoC bounds, and RTE.
- `Battery` — mutable state container. Mutable fields are package-private so only `BatterySimulator` can write them; external readers go through accessor methods.
- `CostCalculator` — multiplies kWh flows by tariffs. Depends on `Tariffs` (DIP). One public method `cost(importKwh, exportKwh)`.
- `Tariffs` (record) — immutable per-kWh prices for the four meter quadrants. Split by tariff even when prices are equal, so time-of-use work only needs a new calculator.
- `EnergyReports` — turns a `CalculationResult` + `Battery` into log lines. Presentation only.
- `CalculationService` — pure orchestrator. Injects the collaborators, runs the pipeline, builds the result, asks `EnergyReports` to log.
- `CalculateController` — single endpoint, multipart because one part is a ~2 MB CSV.

### Data pipeline (inside `CalculationService.calculate`)

1. **Read** — CSV bytes arrive from the HTTP request as `List<String>`.
2. **Parse** — `CsvReadingParser.parse(lines)` skips the header and parses timestamp + four kWh columns into `List<Reading>`. L1/L2/L3 columns are dropped.
3. **Diff to intervals** — `IntervalBuilder.build(readings)` walks pairwise and emits `Interval(time, importKwh, exportKwh)`. **A negative delta is treated as a meter reset**; that single interval is dropped with a WARN. The check is date-free — any future reset behaves the same.
4. **Simulate** — `BatterySimulator.simulate(battery, intervals)` mutates the `Battery` and returns `SimResult(netImport, netExport)`.
5. **Price** — a `CostCalculator` is constructed per request (it's parameterised on the request's `Tariffs`, so a singleton wouldn't make sense). Produces `costNoBattery` and `costWithBattery` from the aggregated totals.
6. **Annualize** — period span derived from `intervals.getFirst().time()` to `.getLast().time()` via `Duration`, so ROI scales correctly with whatever window the CSV covers.
7. **Report** — `EnergyReports.logSummary(result, battery)` emits the human log lines; the service returns `CalculationResult` to the controller.

### Battery simulation rules (in `BatterySimulator.simulate`)

- 15-min interval converted to kWh via `INTERVAL_HOURS = 0.25` (kW × 0.25h = kWh). If input resolution changes, change this constant.
- `exportKwh > 0` and `importKwh > 0` branches are **independent `if`s, not `if/else`** — within a single quarter-hour the meter can show both (the home switched modes mid-interval). Early versions used `else if` and silently dropped the second flow.
- Charge amount = `min(exportKwh, headroom, maxChargePerInterval)`. Discharge (delivered to home) = `min(importKwh, available × rte, maxDischargePerInterval)`.
- **RTE (round-trip efficiency) is applied on discharge only.** Delivering `actual` kWh to the home drains `actual / rte` from the cell. `totalDischarged` tracks **delivered** kWh (the useful number — equals the avoided grid import); energy drained from the cell is `totalDischarged / rte`.
- Energy balance invariant (useful for sanity-checking changes): `totalCharged − totalDischarged/rte ≈ finalCharge − initialCharge`.

### Conventions worth preserving

- **Feature-based packages.** When adding a new concern, give it its own package under `com.rikdevos.energy`. Don't collect types by technical layer (no `records/`, `components/`, `dtos/` folders) — that makes a feature change touch every package.
- **Only `CalculationService` depends across feature packages.** Leaf packages (`parsing`, `simulation`, `pricing`, `reporting`) should never import each other; if they need to, reconsider the seam.
- **Prefer records for value objects** (`Tariffs`, `Reading`, `Interval`, `SimResult`, `CalculationResult`). Classes only when mutation is required (`Battery`).
- **Don't add a `PricingStrategy` / `Parser` / `Simulator` interface until a second implementation exists.** One-implementation interfaces are noise; the seam is already where it needs to be (swap the bean, or inject a different `Tariffs`).
- Package-private is the default visibility. `Numbers` in `reporting/`, mutable fields on `Battery` in `simulation/`, and `BatterySpec.toBattery()` in `web/` are all package-private on purpose.
- `src/main/resources/source.csv` — ~35k rows, one year of 15-min readings. Columns: `time, Import T1 kWh, Import T2 kWh, Export T1 kWh, Export T2 kWh, L1 max W, L2 max W, L3 max W`. Import/export are **cumulative totals** (always increasing until a meter swap).

`EnergyApplication` lives in the root `com.rikdevos.energy` package so `@SpringBootApplication`'s component scan picks up every sub-package automatically — don't move it.

## Dependencies

Only `spring-boot-starter` (no `-web`, no `-data-jpa`, no `-actuator`) is on the classpath. Adding web endpoints, persistence, or actuator requires adding the corresponding starter to `build.gradle` — don't assume they're already wired.
