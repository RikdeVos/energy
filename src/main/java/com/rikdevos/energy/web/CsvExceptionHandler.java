package com.rikdevos.energy.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.rikdevos.energy.parsing.CsvHeaderException;
import com.rikdevos.energy.parsing.InsufficientDataException;

/**
 * Translates parsing-layer domain exceptions into HTTP responses. Lives in {@code web/}
 * so the {@code parsing/} package never has to know about Spring MVC types — keeping
 * the leaf-package independence rule intact. Output is RFC 7807 {@link ProblemDetail},
 * which Spring serializes as {@code application/problem+json} automatically.
 */
@RestControllerAdvice
class CsvExceptionHandler {

    @ExceptionHandler(CsvHeaderException.class)
    ProblemDetail handleHeader(CsvHeaderException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "CSV header is missing required columns");
        pd.setTitle("Invalid CSV header");
        pd.setProperty("missingColumns", e.missing());
        pd.setProperty("actualHeader", e.actualHeader());
        return pd;
    }

    @ExceptionHandler(InsufficientDataException.class)
    ProblemDetail handleInsufficient(InsufficientDataException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "CSV produced too few valid readings to simulate");
        pd.setTitle("Insufficient data");
        pd.setProperty("validRows", e.validCount());
        pd.setProperty("skippedRows", e.skippedCount());
        return pd;
    }

    @ExceptionHandler(InvalidCsvFileException.class)
    ProblemDetail handleInvalidCsvFile(InvalidCsvFileException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.detail());
        pd.setTitle("Invalid CSV file");
        pd.setProperty("reason", e.reason());
        if (e.sizeBytes() != null) {
            pd.setProperty("sizeBytes", e.sizeBytes());
        }
        return pd;
    }

    /**
     * Handles Bean Validation failures on @Valid @RequestPart bodies.
     * The {@code MethodArgumentNotValidException} carries a {@code BindingResult} with
     * one {@code FieldError} per violated constraint. We map those into a domain-named
     * {@code violations} array on the ProblemDetail so clients can render them per-field
     * — matching the structured-property style already used by the other handlers above
     * (e.g. {@code missingColumns}, {@code validRows}).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body failed validation");
        pd.setTitle("Invalid request");

        // LinkedHashMap dedupes multiple constraint failures on the same field into one
        // entry (messages joined with "; ") while preserving the order Bean Validation
        // reported them — so the JSON response is stable across runs and easy to diff.
        LinkedHashMap<String, String> byField = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            
            if (message == null) message = "Invalid field";
            byField.merge(field, message, (existing, addition) -> existing + "; " + addition);
        });

        List<Map<String, String>> violations = byField.entrySet().stream()
                .map(entry -> Map.of("field", entry.getKey(), "message", entry.getValue()))
                .toList();
        pd.setProperty("violations", violations);
        return pd;
    }
}
