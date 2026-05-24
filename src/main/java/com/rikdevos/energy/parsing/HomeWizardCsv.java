package com.rikdevos.energy.parsing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Reads HomeWizard's smart-meter CSV export. Knows the column order
 * ({@code time, Import T1, Import T2, Export T1, Export T2, L1, L2, L3}) and the
 * timestamp pattern {@code yyyy-MM-dd HH:mm}. Sums the T1/T2 tariff quadrants into
 * totals at parse time — downstream consumers only need the totals.
 */
@Component
public class HomeWizardCsv implements ReadingSource {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public List<Reading> read(InputStream in) throws IOException {
        List<Reading> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                String[] p = line.split(",");
                LocalDateTime time = LocalDateTime.parse(p[0].trim(), TIME_FMT);
                double importT1 = Double.parseDouble(p[1].trim());
                double importT2 = Double.parseDouble(p[2].trim());
                double exportT1 = Double.parseDouble(p[3].trim());
                double exportT2 = Double.parseDouble(p[4].trim());
                out.add(new Reading(time, importT1 + importT2, exportT1 + exportT2));
            }
        }
        return out;
    }
}
