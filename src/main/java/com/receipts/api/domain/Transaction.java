package com.receipts.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Owning side of the Receipt 1 -> Transaction 0..1 relationship.
 * The unique constraint on receipt_id is what actually prevents a second
 * transaction from ever being created for the same receipt.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false, unique = true)
    private Receipt receipt;

    private String merchant;

    private LocalDate date;

    private String currency;

    @Column(name = "grand_total", precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "itemize_status", nullable = false)
    private ItemizeStatus itemizeStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tax> taxes = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineItem> lineItems = new ArrayList<>();

    protected Transaction() {
        // JPA
    }

    public Transaction(Receipt receipt) {
        this.receipt = receipt;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.itemizeStatus = ItemizeStatus.NEEDS_REVIEW;
    }

    public Long getId() {
        return id;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = MonetaryAmounts.normalize(grandTotal);
    }

    public ItemizeStatus getItemizeStatus() {
        return itemizeStatus;
    }

    public void setItemizeStatus(ItemizeStatus itemizeStatus) {
        this.itemizeStatus = itemizeStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public List<Tax> getTaxes() {
        return taxes;
    }

    public void replaceTaxes(List<Tax> newTaxes) {
        this.taxes.clear();
        for (Tax tax : newTaxes) {
            tax.setTransaction(this);
            this.taxes.add(tax);
        }
    }

    public List<LineItem> getLineItems() {
        return lineItems;
    }

    public void replaceLineItems(List<LineItem> newItems) {
        this.lineItems.clear();
        for (LineItem item : newItems) {
            item.setTransaction(this);
            this.lineItems.add(item);
        }
    }
}
