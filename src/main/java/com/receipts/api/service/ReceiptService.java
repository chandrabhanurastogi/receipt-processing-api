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
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        try {
            Files.createDirectories(storageDirectory);
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
     * Strips any directory components from the client-supplied filename so a
     * value like "../../etc/passwd" can never influence the storage path -
     * only the base name is kept, and the actual on-disk name is further
     * prefixed with a random UUID.
     */
    private String sanitizeFilename(String originalFilename) {
        String name = originalFilename != null ? originalFilename : "upload";
        name = Path.of(name).getFileName().toString();
        return name.isBlank() ? "upload" : name;
    }
}
