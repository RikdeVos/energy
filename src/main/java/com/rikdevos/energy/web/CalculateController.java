package com.rikdevos.energy.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.rikdevos.energy.web.dto.BatterySpecDto;
import com.rikdevos.energy.web.dto.TariffsDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rikdevos.energy.calculation.CalculationResult;
import com.rikdevos.energy.calculation.CalculationService;

/**
 * Single endpoint {@code POST /calculate}. Multipart because one of the three inputs is
 * a ~2 MB CSV — base64-in-JSON would double the payload and be awkward to build from
 * both curl and the integration test. With multipart, {@code @RequestPart} on a
 * {@code MultipartFile} handles the file and {@code @RequestPart} on a record handles
 * JSON parts via Jackson automatically.
 */
@RestController
public class CalculateController {
    private final CalculationService calculationService;

    public CalculateController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @PostMapping(value = "/calculate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CalculationResult calculate(
            @RequestPart("csv") MultipartFile csv,
            @RequestPart("battery") BatterySpecDto battery,
            @RequestPart("tariffs") TariffsDto tariffs) throws IOException {
        List<String> lines;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        }

        return calculationService.calculate(lines, battery.toBattery(), tariffs.toTarrifs());
    }
}
