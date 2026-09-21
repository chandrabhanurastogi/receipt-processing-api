package com.receipts.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "line_items")
public class LineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    private BigDecimal taxAmount;

    private Integer quantity;

    protected LineItem() {
        // JPA
    }

    public LineItem(String description, BigDecimal amount, BigDecimal taxAmount, Integer quantity) {
        this.description = description;
        this.amount = MonetaryAmounts.normalize(amount);
        this.taxAmount = MonetaryAmounts.normalize(taxAmount);
        this.quantity = quantity;
    }

    public LineItem(String description, BigDecimal amount) {
        this(description, amount, null, null);
    }

    public Long getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
