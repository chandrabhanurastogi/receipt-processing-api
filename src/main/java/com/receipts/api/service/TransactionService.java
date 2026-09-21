package com.receipts.api.service;

import com.receipts.api.domain.Transaction;
import com.receipts.api.dto.request.ItemsPatchRequest;
import com.receipts.api.dto.response.TransactionResponse;
import com.receipts.api.repository.TransactionRepository;
import com.receipts.api.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long transactionId) {
        Transaction transaction = findByIdOrThrow(transactionId);
        return TransactionMapper.toResponse(transaction);
    }

    Transaction findByIdOrThrow(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }

    /**
     * Re-runs auto-itemize from stored OCR, replacing line items only.
     * Implemented in a later stage.
     */
    public TransactionResponse reItemize(Long transactionId) {
        findByIdOrThrow(transactionId);
        throw new UnsupportedOperationException("Re-itemization is not implemented yet");
    }

    /**
     * User override of line items (edit/merge/split via desired-state list).
     * Must validate the proposed state against the transaction's grand total
     * and stored taxes BEFORE mutating any managed entity, returning 409 via
     * ReconciliationConflictException on mismatch. Implemented in a later stage.
     */
    public TransactionResponse updateItems(Long transactionId, ItemsPatchRequest request) {
        findByIdOrThrow(transactionId);
        throw new UnsupportedOperationException("Item override is not implemented yet");
    }
}
