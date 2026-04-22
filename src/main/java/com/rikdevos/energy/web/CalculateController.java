package com.rikdevos.energy.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rikdevos.energy.calculation.CalculationResult;
import com.rikdevos.energy.calculation.CalculationService;
import com.rikdevos.energy.pricing.Tariffs;
import com.rikdevos.energy.simulation.Battery;

/**
 * Single endpoint {@code POST /calculate}. Multipart because one of the three inputs is
 * a ~2 MB CSV — base64-in-JSON would double the payload and be awkward to build from
 * both curl and the integration test. With multipart, {@code @RequestPart} on a
 * {@code MultipartFile} handles the file and {@code @RequestPart} on a record handles
 * JSON parts via Jackson automatically.
 */
@RestController
public class CalculateController {

	private final CalculationService service;

	public CalculateController(CalculationService service) {
		this.service = service;
	}

	@PostMapping(value = "/calculate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CalculationResult calculate(
			@RequestPart("csv") MultipartFile csv,
			@RequestPart("battery") BatterySpec battery,
			@RequestPart("tariffs") Tariffs tariffs) throws IOException {
		List<String> lines;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
			lines = reader.lines().toList();
		}
		// Fresh Battery per request — it mutates during simulate(), so reusing one
		// across requests would leak state between callers.
		return service.calculate(lines, battery.toBattery(), tariffs);
	}

	/**
	 * Request-side view of a {@link Battery}: only the config fields, none of the
	 * mutable running counters. Kept here (alongside the controller) because it only
	 * exists to shape the HTTP request — the domain code uses {@link Battery} directly.
	 */
	public record BatterySpec(
			double maxCapacity,
			double maxPercent,
			double minPercent,
			double maxChargePower,
			double maxDischargePower,
			double rte,
			double price) {

		Battery toBattery() {
			return new Battery(maxCapacity, maxPercent, minPercent,
					maxChargePower, maxDischargePower, rte, price);
		}
	}
}
