package com.rikdevos.energy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Turns raw CSV lines into {@link Reading}s. Single responsibility: CSV format.
 * Knows the column order ({@code time, Import T1, Import T2, Export T1, Export T2, L1, L2, L3})
 * and the timestamp pattern, nothing else. No interval math, no simulation, no logging.
 *
 * <p>Registered as a {@code @Component} so the service receives it via constructor
 * injection — which means a different reading source (Excel, API, mock for tests)
 * just needs a different bean with the same method signature.
 */
@Component
public class CsvReadingParser {

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	public List<Reading> parse(List<String> lines) {
		// -1 capacity hint: we skip the header, so the output is at most lines.size() - 1.
		List<Reading> out = new ArrayList<>(Math.max(0, lines.size() - 1));
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.isEmpty()) continue;
			String[] p = line.split(",");
			LocalDateTime time = LocalDateTime.parse(p[0].trim(), TIME_FMT);
			double importT1 = Double.parseDouble(p[1].trim());
			double importT2 = Double.parseDouble(p[2].trim());
			double exportT1 = Double.parseDouble(p[3].trim());
			double exportT2 = Double.parseDouble(p[4].trim());
			out.add(new Reading(time, importT1, importT2, exportT1, exportT2));
		}
		return out;
	}
}
