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
@Table(name = "taxes")
public class Tax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false)
    private String name;

    @Column(precision = 9, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    private String jurisdiction;

    protected Tax() {
        // JPA
    }

    public Tax(String name, BigDecimal rate, BigDecimal amount, String jurisdiction) {
        this.name = name;
        this.rate = rate;
        this.amount = MonetaryAmounts.normalize(amount);
        this.jurisdiction = jurisdiction;
    }

    public Tax(String name, BigDecimal rate, BigDecimal amount) {
        this(name, rate, amount, null);
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

    public String getName() {
        return name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }
}
