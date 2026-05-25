package com.rikdevos.energy.web.dto;

import com.rikdevos.energy.pricing.Tariffs;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;

public record TariffsDto(

        @PositiveOrZero
        double importT1,

        @PositiveOrZero
        double importT2,

        @PositiveOrZero
        double exportT1,

        @PositiveOrZero
        double exportT2) {
    public Tariffs toTariffs() {
        return new Tariffs(importT1, importT2, exportT1, exportT2);
    }
}
