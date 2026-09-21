package com.receipts.api.ocr;

import com.receipts.api.domain.Receipt;
import com.receipts.api.web.exception.ExtractionFailedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Deterministic OCR stub for this assignment: the uploaded file IS already
 * plain OCR-equivalent text (the fixtures under fixtures/task-a/*.txt), so
 * "running OCR" is reading the stored file's own bytes back as text. This
 * keeps the processing service decoupled from fixture-file handling - it
 * only ever calls {@link OcrProvider#extractRawText(Receipt)} and knows
 * nothing about where the text originally came from. Swapping in a real
 * OCR/VLM vendor later means replacing only this class.
 */
@Component
public class TextFileOcrProvider implements OcrProvider {

    @Override
    public String extractRawText(Receipt receipt) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(receipt.getStoragePath()));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExtractionFailedException(
                    "Unable to read stored receipt file for OCR: receipt " + receipt.getId(), e);
        }
    }
}
