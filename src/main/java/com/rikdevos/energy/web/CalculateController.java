package com.rikdevos.energy.web;

import java.io.IOException;

import com.rikdevos.energy.web.dto.BatterySpecDto;
import com.rikdevos.energy.web.dto.TariffsDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rikdevos.energy.calculation.CalculationResult;
import com.rikdevos.energy.calculation.CalculationService;

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
        try (var in = csv.getInputStream()) {
            return calculationService.calculate(in, battery.toBattery(), tariffs.toTarrifs());
        }
    }
}
