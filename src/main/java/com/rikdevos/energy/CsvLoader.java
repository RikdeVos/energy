package com.rikdevos.energy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class CsvLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CsvLoader.class);

	private List<String> lines = List.of();

	@Override
	public void run(ApplicationArguments args) throws Exception {
		ClassPathResource resource = new ClassPathResource("source.csv");
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			lines = reader.lines().toList();
		}
		log.info("Loaded {} lines from source.csv", lines.size());
	}
}
