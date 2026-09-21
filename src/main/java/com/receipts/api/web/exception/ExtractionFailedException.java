package com.receipts.api.web.exception;

/**
 * Thrown when OCR text exists but cannot be parsed into the minimum
 * required structured fields (e.g. no total found). Mapped to HTTP 422:
 * the request was well-formed and the resource exists, but the server
 * cannot derive meaningful data from it.
 */
public class ExtractionFailedException extends RuntimeException {

    public ExtractionFailedException(String message) {
        super(message);
    }

    public ExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
