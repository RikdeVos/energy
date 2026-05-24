package com.rikdevos.energy.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        ParsedReadings parsed = parser.read(stream(csv));
        List<Reading> readings = parsed.readings();

        assertThat(readings).hasSize(2);
        assertThat(parsed.skippedRows()).isZero();
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

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(2);
        assertThat(parsed.skippedRows()).isZero();
    }

    @Test
    void parsesTimestampPattern() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-06-15 14:30,0,0,0,0,0,0,0
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(1);
        assertThat(parsed.readings().getFirst().time()).isEqualTo(LocalDateTime.of(2024, 6, 15, 14, 30));
    }

    @Test
    void ignoresL1L2L3Columns() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,1.0,2.0,3.0,4.0,9999,9999,9999
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(1);
        assertThat(parsed.readings().getFirst().totalImport()).isEqualTo(3.0);
        assertThat(parsed.readings().getFirst().totalExport()).isEqualTo(7.0);
    }

    @Test
    void throwsOnMissingRequiredColumn() {
        // "time" column is absent — nothing downstream can recover, so we fail fast.
        String csv = """
                Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                10.0,5.0,2.0,1.0,100,100,100
                """;

        assertThatThrownBy(() -> parser.read(stream(csv)))
                .isInstanceOf(CsvHeaderException.class)
                .satisfies(e -> {
                    CsvHeaderException ex = (CsvHeaderException) e;
                    assertThat(ex.missing()).contains("time");
                    assertThat(ex.actualHeader()).startsWith("Import T1 kWh");
                });
    }

    @Test
    void toleratesBadTimestampRow() throws Exception {
        // Middle row's timestamp is garbage — it should be dropped, the other two kept.
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100
                not-a-timestamp,11.0,5.5,2.25,1.25,120,120,120
                2024-01-01 00:30,12.0,6.0,2.5,1.5,140,140,140
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(2);
        assertThat(parsed.skippedRows()).isEqualTo(1);
    }

    @Test
    void toleratesBadNumberRow() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100
                2024-01-01 00:15,not-a-number,5.5,2.25,1.25,120,120,120
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(1);
        assertThat(parsed.skippedRows()).isEqualTo(1);
    }

    @Test
    void toleratesTooFewColumnsRow() throws Exception {
        // Second data row is truncated — missing Export T2 and the L1/L2/L3 columns.
        // Header still has all required names, so the row is skipped but the parse continues.
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100
                2024-01-01 00:15,11.0,5.5,2.25
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(1);
        assertThat(parsed.skippedRows()).isEqualTo(1);
    }

    @Test
    void toleratesBlankCellInRequiredColumn() throws Exception {
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100
                2024-01-01 00:15,,5.5,2.25,1.25,120,120,120
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(1);
        assertThat(parsed.skippedRows()).isEqualTo(1);
    }

    @Test
    void handlesColumnReorder() throws Exception {
        // Columns are in a non-default order — name lookup must still find them.
        String csv = """
                Export T1 kWh,time,Import T2 kWh,Export T2 kWh,Import T1 kWh
                2.0,2024-01-01 00:00,5.0,1.0,10.0
                """;

        ParsedReadings parsed = parser.read(stream(csv));

        assertThat(parsed.readings()).hasSize(1);
        Reading r = parsed.readings().getFirst();
        assertThat(r.totalImport()).isEqualTo(15.0); // 10 + 5
        assertThat(r.totalExport()).isEqualTo(3.0);  // 2 + 1
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
