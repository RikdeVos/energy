package com.rikdevos.energy.web;

/**
 * Signals that the uploaded multipart CSV failed an HTTP-boundary precondition before
 * the parser ever saw the bytes (empty upload, oversized payload). Lives in {@code web/}
 * so the parsing layer stays focused on parsing-level errors — the leaf-package rule
 * means this exception is invisible to {@code parsing/}.
 *
 * <p>Mapped to RFC 7807 {@code ProblemDetail} by {@link CsvExceptionHandler}.
 */
class InvalidCsvFileException extends RuntimeException {

    private final String reason;
    private final String detail;
    private final Long sizeBytes;

    InvalidCsvFileException(String reason, String detail, Long sizeBytes) {
        super(detail);
        this.reason = reason;
        this.detail = detail;
        this.sizeBytes = sizeBytes;
    }

    static InvalidCsvFileException empty() {
        return new InvalidCsvFileException("empty", "CSV upload is empty", null);
    }

    static InvalidCsvFileException tooLarge(long actualBytes, long limitBytes) {
        return new InvalidCsvFileException(
                "too_large",
                "CSV upload exceeds the %d-byte limit".formatted(limitBytes),
                actualBytes);
    }

    String reason() {
        return reason;
    }

    String detail() {
        return detail;
    }

    Long sizeBytes() {
        return sizeBytes;
    }
}
