package com.rikdevos.energy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.rikdevos.energy.web.dto.BatterySpecDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.rikdevos.energy.calculation.CalculationResult;
import com.rikdevos.energy.pricing.Tariffs;

/**
 * End-to-end test via {@link MockMvc}: goes through the full Spring MVC request
 * pipeline (argument resolution, multipart parsing, {@code @RequestPart} binding,
 * Jackson serialization of the response) without spinning up Tomcat. Faster to
 * start than {@code webEnvironment = RANDOM_PORT} and still exercises everything
 * this endpoint actually does — we have no networking concerns to validate here.
 *
 * <p>Assertions are shape/range-based rather than exact numeric equality, so tweaks
 * to the simulation math don't churn this test. A separate unit test on a tiny
 * synthetic dataset is the right place for exact-number pins.
 *
 * <p>The CSV lives in {@code src/main/resources}; Gradle/Spring test classpaths
 * include main resources, so reading it via the classloader works without copying
 * into {@code src/test/resources}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalculateControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void calculatesResultsForRealCsv() throws Exception {
        // Marstek Venus E Gen 3.0 values paired with the user's real tariff rates —
        // together they reproduce the log output the app produced when these were hardcoded.
        BatterySpecDto battery = new BatterySpecDto(5.12, 100, 10, 2.5, 0.8, 0.82, 1099);
        Tariffs tariffs = new Tariffs(0.23236, 0.23236, 0.02, 0.02);

        // try-with-resources because the JVM holds the stream open otherwise, and
        // ClassPathResource gives us a clearer error than a raw null stream if the file
        // has gone missing from the classpath.
        byte[] csvBytes;
        try (var in = new ClassPathResource("source.csv").getInputStream()) {
            csvBytes = in.readAllBytes();
        }

        MockMultipartFile csvPart = new MockMultipartFile(
                "csv", "source.csv", MediaType.TEXT_PLAIN_VALUE, csvBytes);
        // battery + tariffs are JSON-serialized DTOs — matches what a real client would
        // send in a multipart/form-data request with Content-Type: application/json per part.
        MockMultipartFile batteryPart = new MockMultipartFile(
                "battery", "battery.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(battery));
        MockMultipartFile tariffsPart = new MockMultipartFile(
                "tariffs", "tariffs.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(tariffs));

        MvcResult response = mockMvc.perform(multipart("/calculate")
                        .file(csvPart)
                        .file(batteryPart)
                        .file(tariffsPart))
                .andExpect(status().isOk())
                .andReturn();

        CalculationResult r = objectMapper.readValue(
                response.getResponse().getContentAsByteArray(), CalculationResult.class);

        // Shape: one year of 15-min readings → ~35k intervals (minus the one meter-reset drop).
        assertThat(r.intervalsParsed()).isGreaterThan(30_000);

        // Baseline flows are non-trivial.
        assertThat(r.homeImportKwh()).isGreaterThan(0);
        assertThat(r.homeExportKwh()).isGreaterThan(0);

        // Battery reduces both sides of the grid exchange.
        assertThat(r.netImportKwh()).isLessThan(r.homeImportKwh());
        assertThat(r.netExportKwh()).isLessThan(r.homeExportKwh());

        // Battery actually cycled.
        assertThat(r.totalChargedKwh()).isGreaterThan(0);
        assertThat(r.totalDischargedKwh()).isGreaterThan(0);

        // Dataset spans roughly one year — ROI annualization relies on this window.
        assertThat(r.periodYears()).isBetween(0.9, 1.1);

        // Battery spec round-tripped through the request.
        assertThat(r.batteryPrice()).isEqualTo(1099.0);
    }

    @Test
    void missingHeaderColumn_returns400ProblemDetail() throws Exception {
        // Header omits "Export T1 kWh" and "Export T2 kWh" — header validation must fire
        // and the response must be RFC 7807 with the missing columns named.
        String csv = """
                time,Import T1 kWh,Import T2 kWh
                2024-01-01 00:00,10.0,5.0
                2024-01-01 00:15,11.0,5.5
                """;

        MvcResult response = postCalculate(csv)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                response.getResponse().getContentAsByteArray(), Map.class);

        assertThat(body).containsEntry("title", "Invalid CSV header");
        assertThat(body).containsEntry("status", 400);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) body.get("missingColumns");
        assertThat(missing).contains("Export T1 kWh", "Export T2 kWh");
        assertThat((String) body.get("actualHeader")).contains("Import T1 kWh");
    }

    @Test
    void allRowsGarbage_returns400Insufficient() throws Exception {
        // Header is fine, every data row is malformed — parser tolerates each row but
        // CalculationService rejects with InsufficientDataException at the boundary.
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                garbage,x,y,z,w,1,1,1
                also-garbage,x,y,z,w,1,1,1
                """;

        MvcResult response = postCalculate(csv)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                response.getResponse().getContentAsByteArray(), Map.class);

        assertThat(body).containsEntry("title", "Insufficient data");
        assertThat(body).containsEntry("validRows", 0);
        assertThat(body).containsEntry("skippedRows", 2);
    }

    @Test
    void singleValidRow_returns400Insufficient() throws Exception {
        // One row parses cleanly but we need ≥2 for any interval — still a 400.
        String csv = """
                time,Import T1 kWh,Import T2 kWh,Export T1 kWh,Export T2 kWh,L1 max W,L2 max W,L3 max W
                2024-01-01 00:00,10.0,5.0,2.0,1.0,100,100,100
                """;

        MvcResult response = postCalculate(csv)
                .andExpect(status().isBadRequest())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                response.getResponse().getContentAsByteArray(), Map.class);

        assertThat(body).containsEntry("title", "Insufficient data");
        assertThat(body).containsEntry("validRows", 1);
        assertThat(body).containsEntry("skippedRows", 0);
    }

    /** Shared multipart-POST helper for the error-path tests; battery/tariffs values don't matter. */
    private org.springframework.test.web.servlet.ResultActions postCalculate(String csv) throws Exception {
        BatterySpecDto battery = new BatterySpecDto(5.12, 100, 10, 2.5, 0.8, 0.82, 1099);
        Tariffs tariffs = new Tariffs(0.23236, 0.23236, 0.02, 0.02);

        MockMultipartFile csvPart = new MockMultipartFile(
                "csv", "source.csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());
        MockMultipartFile batteryPart = new MockMultipartFile(
                "battery", "battery.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(battery));
        MockMultipartFile tariffsPart = new MockMultipartFile(
                "tariffs", "tariffs.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(tariffs));

        return mockMvc.perform(multipart("/calculate")
                .file(csvPart)
                .file(batteryPart)
                .file(tariffsPart));
    }
}
