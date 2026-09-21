package com.receipts.api.service;

import com.receipts.api.domain.LineItem;
import com.receipts.api.domain.Tax;
import com.receipts.api.domain.Transaction;
import com.receipts.api.dto.request.ItemsPatchRequest;
import com.receipts.api.dto.response.LineItemResponse;
import com.receipts.api.dto.response.TaxResponse;
import com.receipts.api.dto.response.TransactionResponse;
import com.receipts.api.repository.TransactionRepository;
import com.receipts.api.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long transactionId) {
        Transaction transaction = findByIdOrThrow(transactionId);
        return toResponse(transaction);
    }

    Transaction findByIdOrThrow(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }

    /**
     * Re-runs auto-itemize from stored OCR, replacing line items only.
     * Implemented in the processing-flow stage.
     */
    public TransactionResponse reItemize(Long transactionId) {
        findByIdOrThrow(transactionId);
        throw new UnsupportedOperationException("Re-itemization is not implemented yet");
    }

    /**
     * User override of line items (edit/merge/split via desired-state list).
     * Must validate the proposed state against the transaction's grand total
     * and stored taxes BEFORE mutating any managed entity, returning 409 via
     * ReconciliationConflictException on mismatch. Implemented in the
     * processing-flow stage.
     */
    public TransactionResponse updateItems(Long transactionId, ItemsPatchRequest request) {
        findByIdOrThrow(transactionId);
        throw new UnsupportedOperationException("Item override is not implemented yet");
    }

    private TransactionResponse toResponse(Transaction transaction) {
        List<TaxResponse> taxes = transaction.getTaxes().stream()
                .map(this::toTaxResponse)
                .toList();
        List<LineItemResponse> lineItems = transaction.getLineItems().stream()
                .map(this::toLineItemResponse)
                .toList();
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReceipt().getId(),
                transaction.getMerchant(),
                transaction.getDate(),
                transaction.getCurrency(),
                transaction.getGrandTotal(),
                transaction.getItemizeStatus(),
                taxes,
                lineItems
        );
    }

    private TaxResponse toTaxResponse(Tax tax) {
        return new TaxResponse(tax.getId(), tax.getName(), tax.getRate(), tax.getAmount(), tax.getJurisdiction());
    }

    private LineItemResponse toLineItemResponse(LineItem item) {
        return new LineItemResponse(item.getId(), item.getDescription(), item.getAmount(), item.getTaxAmount(), item.getQuantity());
    }
}
