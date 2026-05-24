package com.rikdevos.energy.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit-level checks of {@link HomeWizardCsv}. No Spring context here — the parser
 * has no collaborators, so {@code new HomeWizardCsv()} is the cleanest setup.
 */
class HomeWizardCsvTest {

    private final HomeWizardCsv parser = new HomeWizardCsv();

    @Test
    void skipsHeaderAndSumsTariffQuadrants() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100
                2024-01-01 00:15,11.0,5.5,2.25,1.25,120,120,120
                """;

        List<Reading> readings = parser.read(stream(csv));

        assertThat(readings).hasSize(2);
        assertThat(readings.get(0).time()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
        assertThat(readings.get(0).totalImport()).isEqualTo(15.0); // 10 + 5
        assertThat(readings.get(0).totalExport()).isEqualTo(3.0);  // 2 + 1
        assertThat(readings.get(1).totalImport()).isEqualTo(16.5);
        assertThat(readings.get(1).totalExport()).isEqualTo(3.5);
    }

    @Test
    void skipsBlankLines() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W

                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100

                2024-01-01 00:15,11.0,5.5,2.25,1.25,120,120,120
                """;

        List<Reading> readings = parser.read(stream(csv));

        assertThat(readings).hasSize(2);
    }

    @Test
    void parsesTimestampPattern() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-06-15 14:30,0,0,0,0,0,0,0
                """;

        List<Reading> readings = parser.read(stream(csv));

        assertThat(readings).hasSize(1);
        assertThat(readings.getFirst().time()).isEqualTo(LocalDateTime.of(2024, 6, 15, 14, 30));
    }

    @Test
    void ignoresL1L2L3Columns() throws Exception {
        // L1/L2/L3 max-W values are present but should not affect the parsed reading —
        // they're not in the Reading model at all.
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,1.0,2.0,3.0,4.0,9999,9999,9999
                """;

        List<Reading> readings = parser.read(stream(csv));

        assertThat(readings).hasSize(1);
        assertThat(readings.getFirst().totalImport()).isEqualTo(3.0);
        assertThat(readings.getFirst().totalExport()).isEqualTo(7.0);
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
