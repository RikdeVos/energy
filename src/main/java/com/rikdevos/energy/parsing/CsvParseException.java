package com.rikdevos.energy.parsing;

/**
 * Base type for parse failures that the caller (controller / orchestrator) must turn
 * into an HTTP 4xx response. Subclasses carry the structured detail; the abstract
 * base exists only so a single {@code @ExceptionHandler(CsvParseException.class)}
 * could catch them all if we ever want a fallback handler.
 *
 * <p>Per-row parse failures are <em>not</em> instances of this type — they're logged
 * and counted inside the parser. Throwing here means the input as a whole is bad
 * enough that continuing makes no sense.
 */
public abstract class CsvParseException extends RuntimeException {
    protected CsvParseException(String message) {
        super(message);
    }
}
