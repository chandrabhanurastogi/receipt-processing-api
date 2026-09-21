package com.receipts.api.ocr;

import com.receipts.api.domain.Receipt;

/**
 * Abstraction over OCR so the processing service never depends on a
 * specific OCR/LLM vendor. The stubbed fixture-based implementation is
 * added in the processing-flow stage.
 */
public interface OcrProvider {

    String extractRawText(Receipt receipt);
}
