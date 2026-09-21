package com.receipts.api.controller;

import com.receipts.api.dto.request.ItemsPatchRequest;
import com.receipts.api.dto.response.TransactionResponse;
import com.receipts.api.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    @PostMapping("/{id}/itemize")
    public ResponseEntity<TransactionResponse> reItemize(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.reItemize(id));
    }

    @PatchMapping("/{id}/items")
    public ResponseEntity<TransactionResponse> updateItems(@PathVariable Long id,
                                                            @Valid @RequestBody ItemsPatchRequest request) {
        return ResponseEntity.ok(transactionService.updateItems(id, request));
    }
}
