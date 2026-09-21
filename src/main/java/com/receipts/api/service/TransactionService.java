package com.receipts.api.service;

import com.receipts.api.domain.LineItem;
import com.receipts.api.domain.Receipt;
import com.receipts.api.domain.Tax;
import com.receipts.api.domain.Transaction;
import com.receipts.api.dto.request.ItemRequest;
import com.receipts.api.dto.request.ItemsPatchRequest;
import com.receipts.api.dto.response.TransactionResponse;
import com.receipts.api.extraction.ExtractedLineItem;
import com.receipts.api.extraction.ExtractedReceipt;
import com.receipts.api.extraction.ReceiptExtractor;
import com.receipts.api.repository.TransactionRepository;
import com.receipts.api.web.exception.ExtractionFailedException;
import com.receipts.api.web.exception.ReconciliationConflictException;
import com.receipts.api.web.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final ReceiptExtractor receiptExtractor;
    private final ReconciliationService reconciliationService;

    public TransactionService(TransactionRepository transactionRepository,
                               ReceiptExtractor receiptExtractor,
                               ReconciliationService reconciliationService) {
        this.transactionRepository = transactionRepository;
        this.receiptExtractor = receiptExtractor;
        this.reconciliationService = reconciliationService;
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
     * Re-runs auto-itemize from the receipt's already-persisted raw OCR text
     * (no re-upload, no OCR call). Replaces line items only - merchant, date,
     * currency, grand total, taxes, transaction ID and receipt link are all
     * preserved untouched. Reconciliation is checked against the EXISTING
     * grand total and EXISTING stored taxes, since neither is re-derived here.
     */
    @Transactional
    public TransactionResponse reItemize(Long transactionId) {
        Transaction transaction = findByIdOrThrow(transactionId);
        Receipt receipt = transaction.getReceipt();

        String rawOcrText = receipt.getRawOcrText();
        if (rawOcrText == null || rawOcrText.isBlank()) {
            throw new ExtractionFailedException(
                    "No stored OCR text available for receipt " + receipt.getId() + "; process the receipt first");
        }

        ExtractedReceipt extracted = receiptExtractor.extract(rawOcrText);

        transaction.replaceLineItems(toLineItemEntities(extracted.lineItems()));

        boolean hasReliableLineItems = !extracted.lineItems().isEmpty();
        List<BigDecimal> itemAmounts = extracted.lineItems().stream().map(ExtractedLineItem::amount).toList();
        List<BigDecimal> taxAmounts = transaction.getTaxes().stream().map(Tax::getAmount).toList();
        boolean reconciles = reconciliationService.reconciles(itemAmounts, taxAmounts, transaction.getGrandTotal());
        transaction.setItemizeStatus(reconciliationService.determineStatus(hasReliableLineItems, reconciles));
        transaction.touch();

        Transaction saved = transactionRepository.save(transaction);
        return TransactionMapper.toResponse(saved);
    }

    /**
     * User override of line items via a full desired-state list (edit/merge/
     * split all fall out of the same shape). The proposed state is validated
     * against the transaction's existing grand total and stored taxes BEFORE
     * any managed entity is touched: if it doesn't reconcile, this method
     * returns via an exception without ever calling replaceLineItems, so
     * there is no dirty state for Hibernate to flush on rollback.
     */
    @Transactional
    public TransactionResponse updateItems(Long transactionId, ItemsPatchRequest request) {
        Transaction transaction = findByIdOrThrow(transactionId);

        List<BigDecimal> proposedAmounts = request.items().stream().map(ItemRequest::amount).toList();
        List<BigDecimal> taxAmounts = transaction.getTaxes().stream().map(Tax::getAmount).toList();

        BigDecimal itemsTotal = reconciliationService.sum(proposedAmounts);
        BigDecimal taxTotal = reconciliationService.sum(taxAmounts);
        boolean reconciles = reconciliationService.reconciles(proposedAmounts, taxAmounts, transaction.getGrandTotal());

        if (!reconciles) {
            throw new ReconciliationConflictException(
                    "Proposed line items do not reconcile with the transaction total and stored taxes",
                    itemsTotal, taxTotal, transaction.getGrandTotal());
        }

        transaction.replaceLineItems(toLineItemEntitiesFromRequest(request.items()));
        boolean hasReliableLineItems = !request.items().isEmpty();
        transaction.setItemizeStatus(reconciliationService.determineStatus(hasReliableLineItems, true));
        transaction.touch();

        Transaction saved = transactionRepository.save(transaction);
        return TransactionMapper.toResponse(saved);
    }

    private List<LineItem> toLineItemEntities(List<ExtractedLineItem> extractedLineItems) {
        return extractedLineItems.stream()
                .map(li -> new LineItem(li.description(), li.amount()))
                .toList();
    }

    private List<LineItem> toLineItemEntitiesFromRequest(List<ItemRequest> items) {
        return items.stream()
                .map(i -> new LineItem(i.description(), i.amount(), i.taxAmount(), i.quantity()))
                .toList();
    }
}
