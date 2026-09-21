package com.receipts.api.controller;

import com.receipts.api.domain.Receipt;
import com.receipts.api.dto.response.ReceiptUploadResponse;
import com.receipts.api.dto.response.TransactionResponse;
import com.receipts.api.service.ReceiptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping
    public ResponseEntity<ReceiptUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Receipt receipt = receiptService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ReceiptUploadResponse(receipt.getId()));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<TransactionResponse> process(@PathVariable Long id) {
        receiptService.process(id);
        // Processing-flow stage will return the resulting TransactionResponse here.
        return ResponseEntity.ok().build();
    }
}
