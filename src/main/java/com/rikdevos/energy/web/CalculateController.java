package com.rikdevos.energy.web;

import java.io.IOException;

import com.rikdevos.energy.web.dto.BatterySpecDto;
import com.rikdevos.energy.web.dto.TariffsDto;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rikdevos.energy.calculation.CalculationResult;
import com.rikdevos.energy.calculation.CalculationService;

@RestController
public class CalculateController {

    // 10 MB cap. The bundled source.csv is ~2 MB for one year of 15-min readings,
    // so 10 MB comfortably covers a few years of data while bounding memory use.
    private static final long MAX_CSV_BYTES = 10L * 1024 * 1024;

    private final CalculationService calculationService;

    public CalculateController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @PostMapping(value = "/calculate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CalculationResult calculate(
            @RequestPart("csv") MultipartFile csv,
            @RequestPart("battery") @Valid BatterySpecDto battery,
            @RequestPart("tariffs") @Valid TariffsDto tariffs) throws IOException {
        if (csv.isEmpty()) {
            throw InvalidCsvFileException.empty();
        }
        if (csv.getSize() > MAX_CSV_BYTES) {
            throw InvalidCsvFileException.tooLarge(csv.getSize(), MAX_CSV_BYTES);
        }
        try (var in = csv.getInputStream()) {
            return calculationService.calculate(in, battery.toBattery(), tariffs.toTariffs());
        }
    }
}
