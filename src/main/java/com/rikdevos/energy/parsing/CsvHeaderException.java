package com.rikdevos.energy.parsing;

import java.util.List;

/**
 * Thrown when the CSV header is missing one or more required columns. Carries both
 * the list of missing column names and the actual header line, so the HTTP error
 * body can tell the caller exactly what we expected and what we received.
 */
public class CsvHeaderException extends CsvParseException {

    private final List<String> missing;
    private final String actualHeader;

    public CsvHeaderException(List<String> missing, String actualHeader) {
        super("CSV header is missing required columns: " + missing);
        this.missing = List.copyOf(missing);
        this.actualHeader = actualHeader;
    }

    public List<String> missing() {
        return missing;
    }

    public String actualHeader() {
        return actualHeader;
    }
}
