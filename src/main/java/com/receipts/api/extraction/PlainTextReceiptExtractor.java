package com.receipts.api.extraction;

import com.receipts.api.web.exception.ExtractionFailedException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for the plain-text OCR format supplied in
 * fixtures/task-a/*.txt:
 *
 * <pre>
 * MERCHANT: Cafe Mitte
 * DATE: 2026-03-12
 * CURRENCY: EUR
 *
 * Espresso                    3.50
 * Sandwich                    8.90
 *
 * Subtotal                   15.00
 * VAT 19%                     2.85
 * TOTAL                      17.85
 * </pre>
 *
 * This is intentionally narrow to this one fixture format - not a general
 * OCR/receipt-layout platform. Explicit assumptions:
 * <ul>
 *   <li>Header fields appear as "LABEL: value" lines.</li>
 *   <li>The grand total line starts with "TOTAL" (case-insensitive),
 *       distinct from "Subtotal".</li>
 *   <li>A tax line names the tax (e.g. VAT/GST), a percentage rate, and an
 *       amount, optionally prefixed with "incl." - e.g.
 *       {@code VAT 19%   2.85} or {@code incl. VAT 19%   3.83}.</li>
 *   <li>A line item is any other non-blank line of the form
 *       {@code <description>  <amount with exactly 2 decimals>}, separated
 *       by two or more spaces (matching the fixtures' column alignment).
 *       Lines without a trailing amount (e.g. "Trip fare") or free-text
 *       notes in parentheses are not line items.</li>
 * </ul>
 */
@Component
public class PlainTextReceiptExtractor implements ReceiptExtractor {

    private static final Pattern MERCHANT_LINE = Pattern.compile("(?i)^MERCHANT:\\s*(.+)$");
    private static final Pattern DATE_LINE = Pattern.compile("(?i)^DATE:\\s*(.+)$");
    private static final Pattern CURRENCY_LINE = Pattern.compile("(?i)^CURRENCY:\\s*(.+)$");
    private static final Pattern TOTAL_LINE = Pattern.compile("(?i)^TOTAL\\s+([0-9]+(?:\\.[0-9]+)?)\\s*$");
    private static final Pattern SUBTOTAL_LINE = Pattern.compile("(?i)^SUBTOTAL\\s+([0-9]+(?:\\.[0-9]+)?)\\s*$");
    private static final Pattern TAX_LINE =
            Pattern.compile("(?i)^(?:incl\\.\\s*)?([A-Z]+)\\s+([0-9]+(?:\\.[0-9]+)?)%\\s+([0-9]+(?:\\.[0-9]+)?)\\s*$");
    private static final Pattern LINE_ITEM_LINE = Pattern.compile("^(.+?)\\s{2,}([0-9]+\\.[0-9]{2})\\s*$");

    @Override
    public ExtractedReceipt extract(String rawOcrText) {
        if (rawOcrText == null || rawOcrText.isBlank()) {
            throw new ExtractionFailedException("Raw OCR text is empty; nothing to extract");
        }

        String merchant = null;
        LocalDate date = null;
        String currency = null;
        BigDecimal grandTotal = null;
        List<ExtractedTax> taxes = new ArrayList<>();
        List<ExtractedLineItem> lineItems = new ArrayList<>();

        for (String rawLine : rawOcrText.lines().toList()) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            Matcher merchantMatcher = MERCHANT_LINE.matcher(line);
            if (merchantMatcher.matches()) {
                merchant = merchantMatcher.group(1).strip();
                continue;
            }

            Matcher dateMatcher = DATE_LINE.matcher(line);
            if (dateMatcher.matches()) {
                date = parseDate(dateMatcher.group(1).strip());
                continue;
            }

            Matcher currencyMatcher = CURRENCY_LINE.matcher(line);
            if (currencyMatcher.matches()) {
                currency = currencyMatcher.group(1).strip();
                continue;
            }

            Matcher totalMatcher = TOTAL_LINE.matcher(line);
            if (totalMatcher.matches()) {
                grandTotal = new BigDecimal(totalMatcher.group(1));
                continue;
            }

            Matcher subtotalMatcher = SUBTOTAL_LINE.matcher(line);
            if (subtotalMatcher.matches()) {
                // Subtotal is informational only; reconciliation is derived
                // from line items + taxes vs. the grand total, not restated here.
                continue;
            }

            Matcher taxMatcher = TAX_LINE.matcher(line);
            if (taxMatcher.matches()) {
                String name = taxMatcher.group(1).toUpperCase();
                BigDecimal ratePercent = new BigDecimal(taxMatcher.group(2));
                BigDecimal rate = ratePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                BigDecimal amount = new BigDecimal(taxMatcher.group(3));
                taxes.add(new ExtractedTax(name, rate, amount));
                continue;
            }

            Matcher lineItemMatcher = LINE_ITEM_LINE.matcher(line);
            if (lineItemMatcher.matches()) {
                String description = lineItemMatcher.group(1).strip();
                BigDecimal amount = new BigDecimal(lineItemMatcher.group(2));
                lineItems.add(new ExtractedLineItem(description, amount));
            }
            // Any other line (free-text notes, items without a parseable
            // amount such as "Trip fare") is not reliable data and is skipped.
        }

        if (merchant == null || date == null || currency == null || grandTotal == null) {
            throw new ExtractionFailedException(
                    "Unable to extract required fields (merchant/date/currency/total) from OCR text");
        }

        return new ExtractedReceipt(merchant, date, currency, grandTotal, taxes, lineItems);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ExtractionFailedException("Unable to parse receipt date: " + value, e);
        }
    }
}
