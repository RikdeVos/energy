package com.rikdevos.energy.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads HomeWizard's smart-meter CSV export. Knows the timestamp pattern
 * {@code yyyy-MM-dd HH:mm} and the set of required column <em>names</em> (column order
 * is not assumed — the header drives a name → index map, so an upstream reordering
 * doesn't silently corrupt the parse). Sums the T1/T2 tariff quadrants into totals
 * at parse time; downstream consumers only need the totals.
 *
 * <p><b>Failure policy.</b> A missing required column is fatal — we throw
 * {@link CsvHeaderException} because nothing downstream will produce useful numbers.
 * A single malformed data row, on the other hand, is logged at WARN with its line
 * number and dropped; parsing continues. This mirrors {@link IntervalBuilder}'s
 * meter-reset rule and keeps a single bad line from killing a year of data.
 */
@Component
public class HomeWizardCsv implements ReadingSource {

    private static final Logger log = LoggerFactory.getLogger(HomeWizardCsv.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<String> REQUIRED = List.of(
            "time", "Import T1 kWh", "Import T2 kWh", "Export T1 kWh", "Export T2 kWh");

    @Override
    public ParsedReadings read(InputStream in) throws IOException {
        List<Reading> out = new ArrayList<>();
        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = readFirstNonBlank(reader);
            if (headerLine == null) {
                throw new CsvHeaderException(REQUIRED, "");
            }
            Map<String, Integer> idx = indexHeader(headerLine);
            List<String> missing = REQUIRED.stream().filter(c -> !idx.containsKey(c)).toList();
            if (!missing.isEmpty()) {
                throw new CsvHeaderException(missing, headerLine);
            }

            int lineNumber = 1; // header was line 1
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",", -1); // -1 keeps trailing empties so missing cells stay detectable
                try {
                    Reading r = parseRow(p, idx);
                    out.add(r);
                } catch (DateTimeParseException | IllegalArgumentException e) {
                    log.warn("Skipping CSV row {} — {}: {}", lineNumber,
                            e.getClass().getSimpleName(), e.getMessage());
                    skipped++;
                }
            }
        }
        return new ParsedReadings(out, skipped);
    }

    private static String readFirstNonBlank(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) return trimmed;
        }
        return null;
    }

    private static Map<String, Integer> indexHeader(String headerLine) {
        String[] cols = headerLine.split(",", -1);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            idx.put(cols[i].trim(), i);
        }
        return idx;
    }

    private static Reading parseRow(String[] p, Map<String, Integer> idx) {
        LocalDateTime time = LocalDateTime.parse(cell(p, idx, "time"), TIME_FMT);
        double importT1 = Double.parseDouble(cell(p, idx, "Import T1 kWh"));
        double importT2 = Double.parseDouble(cell(p, idx, "Import T2 kWh"));
        double exportT1 = Double.parseDouble(cell(p, idx, "Export T1 kWh"));
        double exportT2 = Double.parseDouble(cell(p, idx, "Export T2 kWh"));
        return new Reading(time, importT1 + importT2, exportT1 + exportT2);
    }

    private static String cell(String[] p, Map<String, Integer> idx, String name) {
        int i = idx.get(name);
        if (i >= p.length)
            throw new IllegalArgumentException("column '" + name + "' missing from row");
        String v = p[i].trim();
        if (v.isEmpty()) throw new IllegalArgumentException("column '" + name + "' is blank");
        return v;
    }
}
