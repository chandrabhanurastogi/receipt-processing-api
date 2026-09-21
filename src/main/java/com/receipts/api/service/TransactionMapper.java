package com.receipts.api.service;

import com.receipts.api.domain.LineItem;
import com.receipts.api.domain.Tax;
import com.receipts.api.domain.Transaction;
import com.receipts.api.dto.response.LineItemResponse;
import com.receipts.api.dto.response.TaxResponse;
import com.receipts.api.dto.response.TransactionResponse;

import java.util.List;

final class TransactionMapper {

    private TransactionMapper() {
    }

    static TransactionResponse toResponse(Transaction transaction) {
        List<TaxResponse> taxes = transaction.getTaxes().stream()
                .map(TransactionMapper::toTaxResponse)
                .toList();
        List<LineItemResponse> lineItems = transaction.getLineItems().stream()
                .map(TransactionMapper::toLineItemResponse)
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

    private static TaxResponse toTaxResponse(Tax tax) {
        return new TaxResponse(tax.getId(), tax.getName(), tax.getRate(), tax.getAmount(), tax.getJurisdiction());
    }

    private static LineItemResponse toLineItemResponse(LineItem item) {
        return new LineItemResponse(item.getId(), item.getDescription(), item.getAmount(), item.getTaxAmount(), item.getQuantity());
    }
}
