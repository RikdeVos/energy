package com.rikdevos.energy.parsing;

/**
 * Thrown when, after per-row tolerance, the parser produced fewer than two valid
 * readings — so no interval can be built and no simulation is possible. Carries
 * both the valid and skipped counts so the HTTP error body can show the caller why
 * the file was rejected (e.g. "1 valid, 47 skipped" usually means a vendor-format
 * mismatch, not a couple of noisy rows).
 */
public class InsufficientDataException extends CsvParseException {

    private final int validCount;
    private final int skippedCount;

    public InsufficientDataException(int validCount, int skippedCount) {
        super("CSV produced %d valid readings (and %d were skipped); need at least 2 to simulate"
                .formatted(validCount, skippedCount));
        this.validCount = validCount;
        this.skippedCount = skippedCount;
    }

    public int validCount() {
        return validCount;
    }

    public int skippedCount() {
        return skippedCount;
    }
}
