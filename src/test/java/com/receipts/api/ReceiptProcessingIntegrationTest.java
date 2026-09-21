package com.receipts.api;

import com.receipts.api.domain.Transaction;
import com.receipts.api.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the upload -> process -> retrieve vertical slice
 * against the assignment's own fixtures, via real HTTP + H2 persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReceiptProcessingIntegrationTest {

    private static final Path FIXTURES_DIR = Path.of("fixtures", "task-a");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cleanFixture_processesToCompleteWithTaxesAndLineItems() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");

        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Cafe Mitte"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.grandTotal").value(17.85))
                .andExpect(jsonPath("$.itemizeStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.taxes", hasSize(1)))
                .andExpect(jsonPath("$.taxes[0].name").value("VAT"))
                .andExpect(jsonPath("$.taxes[0].amount").value(2.85))
                .andExpect(jsonPath("$.lineItems", hasSize(3)))
                .andExpect(jsonPath("$.lineItems[0].description").value("Espresso"))
                .andExpect(jsonPath("$.lineItems[0].amount").value(3.50));

        // GET must reflect the same persisted state.
        Long transactionId = processAndGetTransactionId(receiptId);
        mockMvc.perform(get("/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value(receiptId))
                .andExpect(jsonPath("$.itemizeStatus").value("COMPLETE"));
    }

    @Test
    void taxOnlyFixture_needsReviewWithNoLineItems() throws Exception {
        long receiptId = uploadFixture("receipt-tax-only.txt");

        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Berlin Taxi GmbH"))
                .andExpect(jsonPath("$.grandTotal").value(24.00))
                .andExpect(jsonPath("$.taxes[0].amount").value(3.83))
                .andExpect(jsonPath("$.lineItems", hasSize(0)))
                .andExpect(jsonPath("$.itemizeStatus").value("NEEDS_REVIEW"));
    }

    @Test
    void mismatchFixture_needsReviewAndPreservesOriginalTotalWithNoBalancingLine() throws Exception {
        long receiptId = uploadFixture("receipt-mismatch.txt");

        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").value(18.50))
                .andExpect(jsonPath("$.itemizeStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.lineItems", hasSize(2)))
                .andExpect(jsonPath("$.lineItems[0].amount").value(4.00))
                .andExpect(jsonPath("$.lineItems[1].amount").value(6.00));
    }

    @Test
    void repeatedProcessing_keepsExactlyOneTransactionWithStableId() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");

        Long firstTransactionId = processAndGetTransactionId(receiptId);
        Long secondTransactionId = processAndGetTransactionId(receiptId);

        assertThat(secondTransactionId).isEqualTo(firstTransactionId);

        List<Transaction> forReceipt = transactionRepository.findAll().stream()
                .filter(t -> t.getReceipt().getId().equals(receiptId))
                .toList();
        assertThat(forReceipt).hasSize(1);
        assertThat(forReceipt.get(0).getGrandTotal()).isEqualByComparingTo(new BigDecimal("17.85"));
    }

    @Test
    void unknownReceipt_returns404() throws Exception {
        mockMvc.perform(post("/receipts/{id}/process", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void unparseableOcrText_returns422() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "garbage.txt", "text/plain", "this is not a receipt at all".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/receipts").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        long receiptId = readReceiptId(uploadResult);

        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void emptyUpload_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/receipts").file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reItemize_replacesLineItemsButPreservesTransactionAndReceipt() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");
        Long transactionId = processAndGetTransactionId(receiptId);
        List<Long> originalItemIds = readLineItemIds(transactionId);

        mockMvc.perform(post("/transactions/{id}/itemize", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.receiptId").value(receiptId))
                .andExpect(jsonPath("$.itemizeStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.lineItems", hasSize(3)))
                .andExpect(jsonPath("$.lineItems[0].description").value("Espresso"));

        // Line items were replaced (new rows), not left untouched, while the
        // transaction identity and receipt link stayed exactly the same.
        List<Long> newItemIds = readLineItemIds(transactionId);
        assertThat(newItemIds).doesNotContainAnyElementsOf(originalItemIds);

        List<Transaction> forReceipt = transactionRepository.findAll().stream()
                .filter(t -> t.getReceipt().getId().equals(receiptId))
                .toList();
        assertThat(forReceipt).hasSize(1);
        assertThat(forReceipt.get(0).getId()).isEqualTo(transactionId);
    }

    @Test
    void reItemize_unknownTransaction_returns404() throws Exception {
        mockMvc.perform(post("/transactions/{id}/itemize", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void successfulPatch_splitReconciles_preservesTotalAndTaxes() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");
        Long transactionId = processAndGetTransactionId(receiptId);

        // Split "Sandwich" (8.90) into two lines that still sum to 15.00 net,
        // matching gold: 3.50 + 4.45 + 4.45 + 2.60 = 15.00, + VAT 2.85 = 17.85.
        String body = """
                {
                  "items": [
                    { "description": "Espresso", "amount": "3.50" },
                    { "description": "Sandwich (half)", "amount": "4.45" },
                    { "description": "Sandwich (half)", "amount": "4.45" },
                    { "description": "Mineral water", "amount": "2.60" }
                  ]
                }
                """;

        mockMvc.perform(patch("/transactions/{id}/items", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").value(17.85))
                .andExpect(jsonPath("$.itemizeStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.lineItems", hasSize(4)))
                .andExpect(jsonPath("$.taxes", hasSize(1)))
                .andExpect(jsonPath("$.taxes[0].amount").value(2.85));
    }

    @Test
    void successfulPatch_toleratesScaleDifferencesInProposedAmounts() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");
        Long transactionId = processAndGetTransactionId(receiptId);

        // "3.5" (scale 1) must still be treated as equal to 3.50 (scale 2):
        // BigDecimal("3.5").equals(BigDecimal("3.50")) is false, but
        // compareTo() based reconciliation must accept it.
        String body = """
                {
                  "items": [
                    { "description": "Espresso", "amount": "3.5" },
                    { "description": "Sandwich", "amount": "8.9" },
                    { "description": "Mineral water", "amount": "2.6" }
                  ]
                }
                """;

        mockMvc.perform(patch("/transactions/{id}/items", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemizeStatus").value("COMPLETE"));
    }

    @Test
    void failedPatch_returns409WithMismatchDetailsAndPreservesExistingState() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");
        Long transactionId = processAndGetTransactionId(receiptId);
        List<Long> originalItemIds = readLineItemIds(transactionId);

        String body = """
                {
                  "items": [
                    { "description": "Made up item", "amount": "1.00" }
                  ]
                }
                """;

        mockMvc.perform(patch("/transactions/{id}/items", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("RECONCILIATION_CONFLICT"))
                .andExpect(jsonPath("$.expectedTotal").value(17.85))
                .andExpect(jsonPath("$.itemsTotal").value(1.00))
                .andExpect(jsonPath("$.taxTotal").value(2.85))
                .andExpect(jsonPath("$.calculatedTotal").value(3.85))
                .andExpect(jsonPath("$.difference").value(14.00));

        // Nothing was mutated: same line items, same total, same taxes.
        mockMvc.perform(get("/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").value(17.85))
                .andExpect(jsonPath("$.itemizeStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.lineItems", hasSize(3)))
                .andExpect(jsonPath("$.taxes[0].amount").value(2.85));
        assertThat(readLineItemIds(transactionId)).isEqualTo(originalItemIds);
    }

    @Test
    void patch_unknownTransaction_returns404() throws Exception {
        String body = """
                { "items": [ { "description": "Espresso", "amount": "3.50" } ] }
                """;

        mockMvc.perform(patch("/transactions/{id}/items", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_malformedRequestMissingAmount_returns400() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");
        Long transactionId = processAndGetTransactionId(receiptId);

        String body = """
                { "items": [ { "description": "Espresso" } ] }
                """;

        mockMvc.perform(patch("/transactions/{id}/items", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_syntacticallyInvalidJsonBody_returns400() throws Exception {
        long receiptId = uploadFixture("receipt-clean.txt");
        Long transactionId = processAndGetTransactionId(receiptId);

        mockMvc.perform(patch("/transactions/{id}/items", transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest());
    }

    private List<Long> readLineItemIds(Long transactionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("lineItems").findValuesAsString("id").stream().map(Long::valueOf).toList();
    }

    private long uploadFixture(String filename) throws Exception {
        byte[] content = Files.readAllBytes(FIXTURES_DIR.resolve(filename));
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", content);

        MvcResult result = mockMvc.perform(multipart("/receipts").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        return readReceiptId(result);
    }

    private long readReceiptId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("receiptId").asLong();
    }

    private Long processAndGetTransactionId(long receiptId) throws Exception {
        MvcResult result = mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
