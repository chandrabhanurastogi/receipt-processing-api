package com.receipts.api.repository;

import com.receipts.api.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Looked up before creating a new Transaction in the process() flow so that
     * repeated processing of the same receipt updates the existing row instead
     * of creating a duplicate (see Receipt 1 -> Transaction 0..1 invariant).
     */
    Optional<Transaction> findByReceiptId(Long receiptId);
}
