package com.rikdevos.energy.web.dto;

import com.rikdevos.energy.pricing.Tariffs;

public record TariffsDto(double importT1, double importT2, double exportT1, double exportT2) {
    public Tariffs toTarrifs() {
        return new Tariffs(importT1, importT2, exportT1, exportT2);
    }
}
