package com.receipts.api.extraction;

import com.receipts.api.web.exception.ExtractionFailedException;

/**
 * Parses raw OCR text into structured fields and auto-itemized line items.
 * Kept separate from OCR itself so extraction (a pure text -> data
 * transform) is independently testable without any I/O.
 */
public interface ReceiptExtractor {

    ExtractedReceipt extract(String rawOcrText) throws ExtractionFailedException;
}
