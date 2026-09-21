package com.receipts.api.extraction;

import com.receipts.api.web.exception.ExtractionFailedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the deterministic parser directly against the assignment's own
 * fixtures and gold.json expectations, independent of HTTP/persistence.
 */
class PlainTextReceiptExtractorTest {

    private static final Path FIXTURES_DIR = Path.of("fixtures", "task-a");

    private final PlainTextReceiptExtractor extractor = new PlainTextReceiptExtractor();

    @Test
    void extractsCleanFixtureMatchingGold() throws IOException {
        ExtractedReceipt extracted = extractor.extract(readFixture("receipt-clean.txt"));

        assertThat(extracted.merchant()).isEqualTo("Cafe Mitte");
        assertThat(extracted.date()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(extracted.currency()).isEqualTo("EUR");
        assertThat(extracted.grandTotal()).isEqualByComparingTo("17.85");

        assertThat(extracted.taxes()).hasSize(1);
        ExtractedTax vat = extracted.taxes().get(0);
        assertThat(vat.name()).isEqualTo("VAT");
        assertThat(vat.rate()).isEqualByComparingTo("0.19");
        assertThat(vat.amount()).isEqualByComparingTo("2.85");

        assertThat(extracted.lineItems()).extracting(ExtractedLineItem::description)
                .containsExactly("Espresso", "Sandwich", "Mineral water");
        assertThat(extracted.lineItems().get(0).amount()).isEqualByComparingTo("3.50");
        assertThat(extracted.lineItems().get(1).amount()).isEqualByComparingTo("8.90");
        assertThat(extracted.lineItems().get(2).amount()).isEqualByComparingTo("2.60");
    }

    @Test
    void extractsTaxOnlyFixtureWithNoLineItems() throws IOException {
        ExtractedReceipt extracted = extractor.extract(readFixture("receipt-tax-only.txt"));

        assertThat(extracted.merchant()).isEqualTo("Berlin Taxi GmbH");
        assertThat(extracted.grandTotal()).isEqualByComparingTo("24.00");
        assertThat(extracted.taxes()).hasSize(1);
        assertThat(extracted.taxes().get(0).amount()).isEqualByComparingTo("3.83");
        assertThat(extracted.lineItems()).isEmpty();
    }

    @Test
    void extractsMismatchFixturePreservingOriginalTotal() throws IOException {
        ExtractedReceipt extracted = extractor.extract(readFixture("receipt-mismatch.txt"));

        assertThat(extracted.merchant()).isEqualTo("Hotel Shop");
        assertThat(extracted.grandTotal()).isEqualByComparingTo("18.50");
        assertThat(extracted.lineItems()).hasSize(2);
        assertThat(extracted.lineItems().get(0).amount()).isEqualByComparingTo("4.00");
        assertThat(extracted.lineItems().get(1).amount()).isEqualByComparingTo("6.00");
        assertThat(extracted.taxes().get(0).amount()).isEqualByComparingTo("1.90");
    }

    @Test
    void malformedTextThatIsMissingRequiredFieldsFailsExtraction() {
        assertThatThrownBy(() -> extractor.extract("this is not a receipt at all"))
                .isInstanceOf(ExtractionFailedException.class);
    }

    @Test
    void blankTextFailsExtraction() {
        assertThatThrownBy(() -> extractor.extract("   "))
                .isInstanceOf(ExtractionFailedException.class);
    }

    private String readFixture(String name) throws IOException {
        return Files.readString(FIXTURES_DIR.resolve(name));
    }
}
