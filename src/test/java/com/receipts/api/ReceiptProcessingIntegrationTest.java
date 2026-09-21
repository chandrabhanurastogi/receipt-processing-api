package com.receipts.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.receipts.api.domain.Transaction;
import com.receipts.api.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
                .andExpect(jsonPath("$.taxes", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.taxes[0].name").value("VAT"))
                .andExpect(jsonPath("$.taxes[0].amount").value(2.85))
                .andExpect(jsonPath("$.lineItems", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.lineItems[0].description").value("Espresso"))
                .andExpect(jsonPath("$.lineItems[0].amount").value(3.50));

        // GET must reflect the same persisted state.
        Long transactionId = processAndGetTransactionId(receiptId);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/transactions/{id}", transactionId))
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
                .andExpect(jsonPath("$.lineItems", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.itemizeStatus").value("NEEDS_REVIEW"));
    }

    @Test
    void mismatchFixture_needsReviewAndPreservesOriginalTotalWithNoBalancingLine() throws Exception {
        long receiptId = uploadFixture("receipt-mismatch.txt");

        mockMvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").value(18.50))
                .andExpect(jsonPath("$.itemizeStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.lineItems", org.hamcrest.Matchers.hasSize(2)))
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
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void emptyUpload_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/receipts").file(emptyFile))
                .andExpect(status().isBadRequest());
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
