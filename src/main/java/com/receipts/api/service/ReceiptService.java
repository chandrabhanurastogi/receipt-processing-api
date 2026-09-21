package com.receipts.api.service;

import com.receipts.api.domain.Receipt;
import com.receipts.api.repository.ReceiptRepository;
import com.receipts.api.web.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final Path storageDirectory;

    public ReceiptService(ReceiptRepository receiptRepository,
                           @Value("${receipts.storage.directory}") String storageDirectory) {
        this.receiptRepository = receiptRepository;
        this.storageDirectory = Path.of(storageDirectory);
    }

    public Receipt upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        try {
            Files.createDirectories(storageDirectory);
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            String storedFilename = UUID.randomUUID() + "-" + originalFilename;
            Path destination = storageDirectory.resolve(storedFilename);
            file.transferTo(destination);

            Receipt receipt = new Receipt(originalFilename, file.getContentType(), destination.toString(), Instant.now());
            return receiptRepository.save(receipt);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded receipt", e);
        }
    }

    public Receipt findByIdOrThrow(Long receiptId) {
        return receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));
    }

    /**
     * OCR + extraction + transaction/tax/line-item creation and reconciliation.
     * Implemented in the processing-flow stage; deliberately not implemented
     * here as this stage only establishes the project skeleton.
     */
    public Receipt process(Long receiptId) {
        findByIdOrThrow(receiptId);
        throw new UnsupportedOperationException("Receipt processing is not implemented yet");
    }
}
