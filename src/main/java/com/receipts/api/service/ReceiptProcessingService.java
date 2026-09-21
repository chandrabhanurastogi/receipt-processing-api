package com.receipts.api.service;

import com.receipts.api.domain.ItemizeStatus;
import com.receipts.api.domain.LineItem;
import com.receipts.api.domain.Receipt;
import com.receipts.api.domain.Tax;
import com.receipts.api.domain.Transaction;
import com.receipts.api.dto.response.TransactionResponse;
import com.receipts.api.extraction.ExtractedLineItem;
import com.receipts.api.extraction.ExtractedReceipt;
import com.receipts.api.extraction.ExtractedTax;
import com.receipts.api.extraction.ReceiptExtractor;
import com.receipts.api.ocr.OcrProvider;
import com.receipts.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orchestrates POST /receipts/{id}/process:
 * receipt -> OCR -> raw text persisted -> extraction -> find-or-create
 * transaction -> replace taxes/line items -> reconcile -> status.
 *
 * Idempotency: {@link TransactionRepository#findByReceiptId(Long)} is
 * looked up before creating a Transaction, so repeated calls for the same
 * receipt always update the same row (stable ID, no duplicate, no orphan).
 */
@Service
public class ReceiptProcessingService {

    private final ReceiptService receiptService;
    private final TransactionRepository transactionRepository;
    private final OcrProvider ocrProvider;
    private final ReceiptExtractor receiptExtractor;
    private final ReconciliationService reconciliationService;

    public ReceiptProcessingService(ReceiptService receiptService,
                                     TransactionRepository transactionRepository,
                                     OcrProvider ocrProvider,
                                     ReceiptExtractor receiptExtractor,
                                     ReconciliationService reconciliationService) {
        this.receiptService = receiptService;
        this.transactionRepository = transactionRepository;
        this.ocrProvider = ocrProvider;
        this.receiptExtractor = receiptExtractor;
        this.reconciliationService = reconciliationService;
    }

    @Transactional
    public TransactionResponse process(Long receiptId) {
        Receipt receipt = receiptService.findByIdOrThrow(receiptId);

        String rawOcrText = ocrProvider.extractRawText(receipt);
        receipt.setRawOcrText(rawOcrText);

        ExtractedReceipt extracted = receiptExtractor.extract(rawOcrText);

        Transaction transaction = transactionRepository.findByReceiptId(receiptId)
                .orElseGet(() -> new Transaction(receipt));

        transaction.setMerchant(extracted.merchant());
        transaction.setDate(extracted.date());
        transaction.setCurrency(extracted.currency());
        transaction.setGrandTotal(extracted.grandTotal());

        transaction.replaceTaxes(toTaxEntities(extracted.taxes()));
        transaction.replaceLineItems(toLineItemEntities(extracted.lineItems()));

        boolean hasReliableLineItems = !extracted.lineItems().isEmpty();
        List<BigDecimal> itemAmounts = extracted.lineItems().stream().map(ExtractedLineItem::amount).toList();
        List<BigDecimal> taxAmounts = extracted.taxes().stream().map(ExtractedTax::amount).toList();
        boolean reconciles = reconciliationService.reconciles(itemAmounts, taxAmounts, extracted.grandTotal());
        ItemizeStatus status = reconciliationService.determineStatus(hasReliableLineItems, reconciles);
        transaction.setItemizeStatus(status);
        transaction.touch();

        Transaction saved = transactionRepository.save(transaction);
        return TransactionMapper.toResponse(saved);
    }

    private List<Tax> toTaxEntities(List<ExtractedTax> extractedTaxes) {
        return extractedTaxes.stream()
                .map(t -> new Tax(t.name(), t.rate(), t.amount()))
                .toList();
    }

    private List<LineItem> toLineItemEntities(List<ExtractedLineItem> extractedLineItems) {
        return extractedLineItems.stream()
                .map(li -> new LineItem(li.description(), li.amount()))
                .toList();
    }
}
