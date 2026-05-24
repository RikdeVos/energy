package com.rikdevos.energy.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
}
